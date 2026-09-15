package com.xtramile.patient.web.dto;

import com.xtramile.patient.domain.Gender;
import com.xtramile.patient.validation.AustralianPhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body of {@code PUT /api/v1/patients/{id}}.
 *
 * <p>Modelled as its own type rather than reusing {@link CreatePatientRequest} because the two
 * operations genuinely have different contracts: an update must carry the {@code version} the
 * client last read, so that a concurrent edit is rejected instead of silently overwritten.
 */
@Schema(name = "UpdatePatientRequest")
public record UpdatePatientRequest(

        @Schema(example = "Jane")
        @NotBlank(message = "first name is required")
        @Size(max = 60, message = "first name must be at most 60 characters")
        String firstName,

        @Schema(example = "Citizen")
        @NotBlank(message = "last name is required")
        @Size(max = 60, message = "last name must be at most 60 characters")
        String lastName,

        @Schema(example = "1985-04-12")
        @NotNull(message = "date of birth is required")
        @Past(message = "date of birth must be in the past")
        LocalDate dateOfBirth,

        @Schema(example = "FEMALE")
        @NotNull(message = "gender is required")
        Gender gender,

        @Schema(example = "0412 345 678")
        @NotBlank(message = "phone number is required")
        @AustralianPhone
        String phoneNo,

        @NotNull(message = "address is required")
        @Valid
        AddressRequest address,

        @Schema(description = "Version last read by the client; used for optimistic locking", example = "0")
        @NotNull(message = "version is required so concurrent edits can be detected")
        @PositiveOrZero(message = "version must not be negative")
        Long version) {
}
