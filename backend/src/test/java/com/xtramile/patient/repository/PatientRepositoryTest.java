package com.xtramile.patient.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.xtramile.patient.config.JpaConfig;
import com.xtramile.patient.domain.AustralianAddress;
import com.xtramile.patient.domain.AustralianState;
import com.xtramile.patient.domain.Gender;
import com.xtramile.patient.domain.Patient;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tests the JPQL that the service layer's mocks stand in for.
 *
 * <p>The search query is the one piece of this application whose behaviour lives in SQL rather
 * than in Java, so it is exercised against a real (in-memory) database with the real Flyway
 * schema. {@code @DataJpaTest} rolls each test back, so the tests are order independent.
 *
 * <p>{@code replace = NONE} keeps the H2 data source from {@code application-test.yml} instead of
 * substituting a generated one, which is what makes Flyway - and therefore the real schema,
 * constraints and indexes - apply here too.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// @DataJpaTest only loads repositories and JPA infrastructure, not our own @Configuration
// classes, so @EnableJpaAuditing has to be imported explicitly for createdAt/updatedAt to be set.
@Import(JpaConfig.class)
@ActiveProfiles("test")
@DisplayName("PatientRepository")
class PatientRepositoryTest {

    @Autowired
    private PatientRepository repository;

    @BeforeEach
    void seed() {
        repository.saveAll(java.util.List.of(
                patient("PAT-000001", "Jane", "Citizen"),
                patient("PAT-000002", "John", "Smith"),
                patient("PAT-000003", "Janet", "Jones"),
                patient("PAT-000004", "Mary", "Janeway")));
        repository.flush();
    }

    private static Patient patient(String pid, String first, String last) {
        return new Patient(pid, first, last, LocalDate.of(1985, 4, 12), Gender.FEMALE, "+61412345678",
                new AustralianAddress("12 Wallaby Way", "Sydney", AustralianState.NSW, "2000"));
    }

    @Test
    @DisplayName("search matches a first name, case insensitively")
    void matchesFirstName() {
        Page<Patient> result = repository.search("%jane%", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Patient::getPid)
                // Jane (first name), Janet (prefix) and Janeway (last name) all contain "jane".
                .containsExactlyInAnyOrder("PAT-000001", "PAT-000003", "PAT-000004");
    }

    @Test
    @DisplayName("search matches a last name")
    void matchesLastName() {
        Page<Patient> result = repository.search("%smith%", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Patient::getPid).containsExactly("PAT-000002");
    }

    @Test
    @DisplayName("search matches the PID, which is the other half of the required search feature")
    void matchesPid() {
        Page<Patient> result = repository.search("%pat-000002%", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Patient::getPid).containsExactly("PAT-000002");
    }

    @Test
    @DisplayName("search matches a full 'first last' name, which no single column contains")
    void matchesFullName() {
        Page<Patient> result = repository.search("%jane citizen%", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Patient::getPid).containsExactly("PAT-000001");
    }

    @Test
    @DisplayName("search returns an empty page rather than null when nothing matches")
    void returnsEmptyPage() {
        Page<Patient> result = repository.search("%nobody%", PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("paging happens in the database: totalElements counts all matches, content only the page")
    void pagesInTheDatabase() {
        Page<Patient> firstPage = repository.search("%jane%", PageRequest.of(0, 2, Sort.by("pid")));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.isLast()).isFalse();
    }

    @Test
    @DisplayName("findByPid resolves the business identifier")
    void findsByPid() {
        assertThat(repository.findByPid("PAT-000003"))
                .isPresent()
                .get()
                .extracting(Patient::getFirstName)
                .isEqualTo("Janet");
    }

    @Test
    @DisplayName("the unique constraint on PID is enforced by the database, not only by the application")
    void pidIsUniqueInTheSchema() {
        Patient duplicate = patient("PAT-000001", "Impostor", "Citizen");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            repository.save(duplicate);
            repository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("auditing populates createdAt and updatedAt without any application code")
    void auditingIsWired() {
        Patient saved = repository.findByPid("PAT-000001").orElseThrow();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isNotNull();
    }
}
