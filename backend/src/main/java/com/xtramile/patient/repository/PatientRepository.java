package com.xtramile.patient.repository;

import com.xtramile.patient.domain.Patient;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByPid(String pid);

    boolean existsByPid(String pid);

    /**
     * Free text search across PID, given name, family name and the concatenated full name.
     *
     * <p>Written as an explicit JPQL query rather than a derived method name because the
     * derived equivalent ({@code findByPidContainingIgnoreCaseOrFirstNameContainingIgnoreCase...})
     * is unreadable and still cannot express the "first last" match.
     *
     * <p>{@code term} is expected to be pre-lowercased and wrapped in {@code %} by the service;
     * doing it there keeps the wildcard policy in one testable place.
     *
     * <p>Note this is a leading wildcard {@code LIKE}, which cannot use a B-tree index. That is an
     * accepted trade-off at this data volume - see the README for the full text search path we
     * would take when the table grows.
     */
    @Query("""
            SELECT p FROM Patient p
            WHERE LOWER(p.pid) LIKE :term
               OR LOWER(p.firstName) LIKE :term
               OR LOWER(p.lastName) LIKE :term
               OR LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE :term
            """)
    Page<Patient> search(@Param("term") String term, Pageable pageable);
}
