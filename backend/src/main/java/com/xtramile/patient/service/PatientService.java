package com.xtramile.patient.service;

import com.xtramile.patient.domain.Patient;
import com.xtramile.patient.repository.PatientRepository;
import com.xtramile.patient.service.exception.InvalidSortPropertyException;
import com.xtramile.patient.service.exception.PatientNotFoundException;
import com.xtramile.patient.service.exception.StalePatientDataException;
import com.xtramile.patient.validation.PhoneNumbers;
import com.xtramile.patient.web.dto.CreatePatientRequest;
import com.xtramile.patient.web.dto.PageResponse;
import com.xtramile.patient.web.dto.PatientResponse;
import com.xtramile.patient.web.dto.UpdatePatientRequest;
import com.xtramile.patient.web.mapper.PatientMapper;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Patient aggregate.
 *
 * <p>This is where the use cases live. The controller only translates HTTP to method calls, and
 * the repository only talks SQL; everything that a second delivery mechanism (a batch importer,
 * an HL7 feed, a message listener) would need to repeat lives here instead.
 *
 * <p>The class is read only by default and opts individual write operations into a read/write
 * transaction, so an accidental write from a query path fails loudly rather than silently
 * committing.
 */
@Service
@Transactional(readOnly = true)
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    /**
     * Properties a client may sort by. Everything here is either indexed or cheap to sort, and
     * the list doubles as the public, documented contract for the {@code sort} query parameter.
     */
    static final Set<String> SORTABLE_PROPERTIES =
            Set.of("pid", "firstName", "lastName", "dateOfBirth", "gender", "createdAt", "updatedAt");

    /** Stable, predictable ordering for clients that do not ask for one. */
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("lastName"), Sort.Order.asc("firstName"));

    /** Guards the database against a client asking for one very large page. */
    static final int MAX_PAGE_SIZE = 100;

    private final PatientRepository repository;
    private final PatientMapper mapper;
    private final PidGenerator pidGenerator;

    public PatientService(PatientRepository repository, PatientMapper mapper, PidGenerator pidGenerator) {
        this.repository = repository;
        this.mapper = mapper;
        this.pidGenerator = pidGenerator;
    }

    /**
     * Returns one page of patients, optionally filtered by a free text term that is matched
     * against PID, first name, last name and full name.
     *
     * <p>Paging is done in the database, never in memory: the grid must stay responsive when the
     * table holds millions of rows.
     */
    public PageResponse<PatientResponse> search(String query, Pageable pageable) {
        Pageable safePageable = sanitise(pageable);
        String term = normaliseSearchTerm(query);

        Page<Patient> page = (term == null)
                ? repository.findAll(safePageable)
                : repository.search(term, safePageable);

        return PageResponse.from(page.map(mapper::toResponse));
    }

    public PatientResponse findById(Long id) {
        return mapper.toResponse(requirePatient(id));
    }

    public PatientResponse findByPid(String pid) {
        return repository.findByPid(pid)
                .map(mapper::toResponse)
                .orElseThrow(() -> PatientNotFoundException.byPid(pid));
    }

    @Transactional
    public PatientResponse create(CreatePatientRequest request) {
        Patient patient = new Patient(
                pidGenerator.next(),
                request.firstName().trim(),
                request.lastName().trim(),
                request.dateOfBirth(),
                request.gender(),
                PhoneNumbers.normalise(request.phoneNo()),
                mapper.toDomain(request.address()));

        Patient saved = repository.save(patient);
        log.info("Created patient {} (id={})", saved.getPid(), saved.getId());
        return mapper.toResponse(saved);
    }

    /**
     * Replaces the editable attributes of an existing patient.
     *
     * <p>The version the client last read is compared before anything is written. That check is
     * belt and braces on top of the JPA {@code @Version} column: comparing here produces a clear
     * 409 with both version numbers, while the column still protects against the narrow race
     * between this read and the flush.
     */
    @Transactional
    public PatientResponse update(Long id, UpdatePatientRequest request) {
        Patient patient = requirePatient(id);

        if (!patient.getVersion().equals(request.version())) {
            throw new StalePatientDataException(id, request.version(), patient.getVersion());
        }

        patient.applyDemographics(
                request.firstName().trim(),
                request.lastName().trim(),
                request.dateOfBirth(),
                request.gender(),
                PhoneNumbers.normalise(request.phoneNo()),
                mapper.toDomain(request.address()));

        // saveAndFlush, not save: it forces the UPDATE (and therefore the optimistic lock check)
        // to happen inside this method, so an OptimisticLockingFailureException is translated by
        // our handler instead of escaping during the post-commit flush.
        Patient saved = repository.saveAndFlush(patient);
        log.info("Updated patient {} (id={}) to version {}", saved.getPid(), id, saved.getVersion());
        return mapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Patient patient = requirePatient(id);
        repository.delete(patient);
        log.info("Deleted patient {} (id={})", patient.getPid(), id);
    }

    private Patient requirePatient(Long id) {
        return repository.findById(id).orElseThrow(() -> PatientNotFoundException.byId(id));
    }

    /**
     * Lower-cases the search term and wraps it in SQL wildcards, so that the wildcard policy is
     * defined once here rather than at every call site.
     *
     * @return {@code null} when the caller did not supply a usable term, meaning "no filter"
     */
    static String normaliseSearchTerm(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        // Escape the LIKE metacharacters so a user searching for "50%" does not match everything.
        String escaped = query.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * Caps the page size and rejects sort properties that are not on the allow list, falling back
     * to a deterministic default sort when none was requested.
     */
    Pageable sanitise(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, 20, DEFAULT_SORT);
        }
        Sort sort = pageable.getSort();
        List<String> rejected = sort.stream()
                .map(Sort.Order::getProperty)
                .filter(property -> !SORTABLE_PROPERTIES.contains(property))
                .toList();
        if (!rejected.isEmpty()) {
            throw new InvalidSortPropertyException(rejected.get(0), SORTABLE_PROPERTIES.stream().sorted().toList());
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                sort.isSorted() ? sort : DEFAULT_SORT);
    }
}
