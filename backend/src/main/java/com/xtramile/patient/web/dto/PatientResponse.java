package com.xtramile.patient.web.dto;

import com.xtramile.patient.domain.Gender;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Outbound patient representation.
 *
 * <p>A dedicated response type (rather than serialising the entity) keeps the HTTP contract
 * stable when the persistence model changes, and lets us add derived, display oriented fields
 * such as {@code fullName}, {@code age} and the locally formatted phone number.
 *
 * @param version the optimistic locking token the client must echo back on update
 */
public record PatientResponse(
        Long id,
        String pid,
        String firstName,
        String lastName,
        String fullName,
        LocalDate dateOfBirth,
        Integer age,
        Gender gender,
        String phoneNo,
        String phoneNoLocal,
        AddressResponse address,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
