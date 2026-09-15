package com.xtramile.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xtramile.patient.domain.Patient;
import com.xtramile.patient.repository.PatientRepository;
import com.xtramile.patient.service.exception.InvalidSortPropertyException;
import com.xtramile.patient.service.exception.PatientNotFoundException;
import com.xtramile.patient.service.exception.StalePatientDataException;
import com.xtramile.patient.testsupport.PatientTestData;
import com.xtramile.patient.web.dto.CreatePatientRequest;
import com.xtramile.patient.web.dto.PageResponse;
import com.xtramile.patient.web.dto.PatientResponse;
import com.xtramile.patient.web.dto.UpdatePatientRequest;
import com.xtramile.patient.web.mapper.PatientMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for the service layer.
 *
 * <p>No Spring context and no database: the repository and the PID generator are mocked, so each
 * test runs in milliseconds and a failure points at exactly one piece of business logic. The
 * database behaviour those mocks stand in for is covered separately by
 * {@code PatientRepositoryTest} and {@code PatientApiIntegrationTest}.
 *
 * <p>The mapper is a real instance rather than a mock: it is pure, side effect free logic, and
 * mocking it would make these tests assert on mock interactions instead of on real output.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PatientService")
class PatientServiceTest {

    /** Fixed so that the derived age assertion cannot break tomorrow. */
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PatientRepository repository;

    @Mock
    private PidGenerator pidGenerator;

    @SuppressWarnings("unused") // injected into PatientService below
    private final PatientMapper mapper = new PatientMapper(FIXED_CLOCK);

    private PatientService service;

    @Captor
    private ArgumentCaptor<Patient> patientCaptor;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new PatientService(repository, mapper, pidGenerator);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("allocates the PID from the generator rather than trusting the client")
        void allocatesPid() {
            when(pidGenerator.next()).thenReturn("PAT-000007");
            when(repository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

            service.create(PatientTestData.createRequest());

            verify(repository).save(patientCaptor.capture());
            assertThat(patientCaptor.getValue().getPid()).isEqualTo("PAT-000007");
        }

        @Test
        @DisplayName("normalises the phone number to E.164 before persisting")
        void normalisesPhone() {
            when(pidGenerator.next()).thenReturn("PAT-000001");
            when(repository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

            CreatePatientRequest request = new CreatePatientRequest(
                    "Jane", "Citizen", PatientTestData.DOB,
                    com.xtramile.patient.domain.Gender.FEMALE,
                    "(02) 9876 5432",
                    PatientTestData.addressRequest());

            PatientResponse response = service.create(request);

            verify(repository).save(patientCaptor.capture());
            assertThat(patientCaptor.getValue().getPhoneNo()).isEqualTo("+61298765432");
            // ...and the caller still sees it in the form a human reads.
            assertThat(response.phoneNoLocal()).isEqualTo("(02) 9876 5432");
        }

        @Test
        @DisplayName("trims surrounding whitespace from names")
        void trimsNames() {
            when(pidGenerator.next()).thenReturn("PAT-000001");
            when(repository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

            CreatePatientRequest request = new CreatePatientRequest(
                    "  Jane  ", " Citizen ", PatientTestData.DOB,
                    com.xtramile.patient.domain.Gender.FEMALE, "0412345678",
                    PatientTestData.addressRequest());

            service.create(request);

            verify(repository).save(patientCaptor.capture());
            assertThat(patientCaptor.getValue().getFirstName()).isEqualTo("Jane");
            assertThat(patientCaptor.getValue().getLastName()).isEqualTo("Citizen");
        }

        @Test
        @DisplayName("returns a response carrying the derived age and formatted address")
        void returnsDerivedFields() {
            when(pidGenerator.next()).thenReturn("PAT-000001");
            when(repository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

            PatientResponse response = service.create(PatientTestData.createRequest());

            // Born 1985-04-12, clock fixed at 2026-06-01 -> 41.
            assertThat(response.age()).isEqualTo(41);
            assertThat(response.fullName()).isEqualTo("Jane Citizen");
            assertThat(response.address().formatted()).isEqualTo("12 Wallaby Way, Sydney NSW 2000");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("applies the new demographics when the version matches")
        void updatesWhenVersionMatches() {
            Patient existing = PatientTestData.persistedPatient(1L, "PAT-000001", 3L);
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.saveAndFlush(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

            PatientResponse response = service.update(1L, PatientTestData.updateRequest(3L));

            assertThat(response.lastName()).isEqualTo("Doe");
            verify(repository).saveAndFlush(existing);
        }

        @Test
        @DisplayName("rejects a stale version with the expected and actual values, and writes nothing")
        void rejectsStaleVersion() {
            Patient existing = PatientTestData.persistedPatient(1L, "PAT-000001", 5L);
            when(repository.findById(1L)).thenReturn(Optional.of(existing));

            UpdatePatientRequest stale = PatientTestData.updateRequest(2L);

            assertThatThrownBy(() -> service.update(1L, stale))
                    .isInstanceOf(StalePatientDataException.class)
                    .satisfies(e -> {
                        StalePatientDataException ex = (StalePatientDataException) e;
                        assertThat(ex.getExpectedVersion()).isEqualTo(2L);
                        assertThat(ex.getActualVersion()).isEqualTo(5L);
                    });

            // The important half of this test: nothing was written.
            verify(repository, never()).saveAndFlush(any());
            assertThat(existing.getLastName()).isEqualTo("Citizen");
        }

        @Test
        @DisplayName("fails with 404 semantics when the patient does not exist")
        void failsWhenMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99L, PatientTestData.updateRequest(0L)))
                    .isInstanceOf(PatientNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("keeps the PID immutable across an update")
        void pidIsImmutable() {
            Patient existing = PatientTestData.persistedPatient(1L, "PAT-000001", 0L);
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.saveAndFlush(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

            PatientResponse response = service.update(1L, PatientTestData.updateRequest(0L));

            assertThat(response.pid()).isEqualTo("PAT-000001");
            verifyNoInteractions(pidGenerator);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void deletesExistingPatient() {
            Patient existing = PatientTestData.persistedPatient(1L, "PAT-000001", 0L);
            when(repository.findById(1L)).thenReturn(Optional.of(existing));

            service.delete(1L);

            verify(repository).delete(existing);
        }

        @Test
        @DisplayName("fails rather than silently succeeding when the patient does not exist")
        void failsWhenMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(PatientNotFoundException.class);
            verify(repository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("lists every patient when no term is supplied")
        void noTermListsAll() {
            when(repository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(PatientTestData.persistedPatient(1L, "PAT-000001", 0L))));

            PageResponse<PatientResponse> page = service.search(null, PageRequest.of(0, 10));

            assertThat(page.content()).hasSize(1);
            verify(repository, never()).search(anyString(), any());
        }

        @ParameterizedTest(name = "a {0} search term is treated as no filter")
        @ValueSource(strings = {"", "   "})
        void blankTermListsAll(String term) {
            when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            service.search(term, PageRequest.of(0, 10));

            verify(repository, never()).search(anyString(), any());
        }

        @Test
        @DisplayName("lower-cases the term and wraps it in wildcards exactly once")
        void wrapsTermInWildcards() {
            when(repository.search(eq("%jane%"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            service.search("  JANE  ", PageRequest.of(0, 10));

            verify(repository).search(eq("%jane%"), any(Pageable.class));
        }

        @Test
        @DisplayName("escapes LIKE metacharacters so '%' is searched for, not interpreted")
        void escapesLikeWildcards() {
            when(repository.search(anyString(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            service.search("50%_x", PageRequest.of(0, 10));

            verify(repository).search(eq("%50\\%\\_x%"), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("paging and sorting guard rails")
    class PagingGuards {

        @Test
        @DisplayName("caps an oversized page request at MAX_PAGE_SIZE")
        void capsPageSize() {
            Pageable sanitised = service.sanitise(PageRequest.of(0, 5_000));

            assertThat(sanitised.getPageSize()).isEqualTo(PatientService.MAX_PAGE_SIZE);
        }

        @Test
        @DisplayName("applies a deterministic default sort when the client asks for none")
        void appliesDefaultSort() {
            Pageable sanitised = service.sanitise(PageRequest.of(0, 10));

            assertThat(sanitised.getSort()).isEqualTo(PatientService.DEFAULT_SORT);
        }

        @Test
        @DisplayName("keeps a sort the client did ask for")
        void keepsRequestedSort() {
            Sort requested = Sort.by(Sort.Direction.DESC, "dateOfBirth");

            Pageable sanitised = service.sanitise(PageRequest.of(0, 10, requested));

            assertThat(sanitised.getSort()).isEqualTo(requested);
        }

        @Test
        @DisplayName("rejects a sort property that is not on the allow list")
        void rejectsUnknownSortProperty() {
            Pageable malicious = PageRequest.of(0, 10, Sort.by("address.postcode"));

            assertThatThrownBy(() -> service.sanitise(malicious))
                    .isInstanceOf(InvalidSortPropertyException.class)
                    .hasMessageContaining("address.postcode")
                    .hasMessageContaining("lastName");
        }

        @Test
        @DisplayName("falls back to a sane page request when handed an unpaged Pageable")
        void handlesUnpaged() {
            Pageable sanitised = service.sanitise(Pageable.unpaged());

            assertThat(sanitised.getPageNumber()).isZero();
            assertThat(sanitised.getPageSize()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("lookup")
    class Lookup {

        @Test
        void findByIdReturnsMappedResponse() {
            when(repository.findById(1L))
                    .thenReturn(Optional.of(PatientTestData.persistedPatient(1L, "PAT-000001", 0L)));

            assertThat(service.findById(1L).pid()).isEqualTo("PAT-000001");
        }

        @Test
        void findByPidThrowsWhenAbsent() {
            when(repository.findByPid("PAT-999999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findByPid("PAT-999999"))
                    .isInstanceOf(PatientNotFoundException.class)
                    .hasMessageContaining("PAT-999999");
        }
    }

    /** Guards the contract the mapper relies on: age is derived from the injected clock. */
    @Test
    @DisplayName("age is computed against the injected clock, not the wall clock")
    void ageUsesInjectedClock() {
        Patient patient = PatientTestData.patient("PAT-000001");

        assertThat(patient.getAge(LocalDate.of(2026, 4, 11))).isEqualTo(40);
        assertThat(patient.getAge(LocalDate.of(2026, 4, 12))).isEqualTo(41);
    }
}
