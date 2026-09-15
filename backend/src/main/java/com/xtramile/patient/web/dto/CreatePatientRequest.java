package com.xtramile.patient.web.dto;

import com.xtramile.patient.domain.Gender;
import com.xtramile.patient.validation.AustralianPhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body of {@code POST /api/v1/patients}.
 *
 * <p>There is deliberately no {@code pid} field: the PID is allocated by the server (see
 * {@code PidGenerator}), which is the only way to guarantee it is unique without trusting the
 * client.
 */
@Schema(name = "CreatePatientRequest")
public record CreatePatientRequest(

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
        AddressRequest address) {
}
