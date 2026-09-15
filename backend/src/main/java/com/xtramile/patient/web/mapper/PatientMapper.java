package com.xtramile.patient.web.mapper;

import com.xtramile.patient.domain.AustralianAddress;
import com.xtramile.patient.domain.Patient;
import com.xtramile.patient.validation.PhoneNumbers;
import com.xtramile.patient.web.dto.AddressRequest;
import com.xtramile.patient.web.dto.AddressResponse;
import com.xtramile.patient.web.dto.PatientResponse;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Translates between the persistence model and the HTTP contract.
 *
 * <p>Hand written rather than generated (MapStruct/ModelMapper): with a single aggregate the
 * mapping is a few dozen lines, it is trivially unit testable, and it avoids an annotation
 * processor plus a layer of indirection that would have to be taken on trust.
 *
 * <p>A {@link Clock} is injected so that the derived {@code age} is deterministic in tests.
 */
@Component
public class PatientMapper {

    private final Clock clock;

    public PatientMapper(Clock clock) {
        this.clock = clock;
    }

    public PatientResponse toResponse(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getPid(),
                patient.getFirstName(),
                patient.getLastName(),
                patient.getFullName(),
                patient.getDateOfBirth(),
                patient.getAge(LocalDate.now(clock)),
                patient.getGender(),
                patient.getPhoneNo(),
                PhoneNumbers.toLocalFormat(patient.getPhoneNo()),
                toResponse(patient.getAddress()),
                patient.getVersion(),
                patient.getCreatedAt(),
                patient.getUpdatedAt());
    }

    public AddressResponse toResponse(AustralianAddress address) {
        return new AddressResponse(
                address.getStreet(),
                address.getSuburb(),
                address.getState(),
                address.getState().displayName(),
                address.getPostcode(),
                address.toString());
    }

    /** Trims free text so that " Jane " and "Jane" do not become two different patients. */
    public AustralianAddress toDomain(AddressRequest request) {
        return new AustralianAddress(
                request.street().trim(),
                request.suburb().trim(),
                request.state(),
                request.postcode().trim());
    }
}
