package com.xtramile.patient.web.dto;

import com.xtramile.patient.domain.AustralianState;
import com.xtramile.patient.validation.PostcodeMatchesState;
import com.xtramile.patient.validation.PostcodeMatchesStateValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Inbound Australian address. */
@PostcodeMatchesState
@Schema(name = "AddressRequest", description = "Australian residential address")
public record AddressRequest(

        @Schema(example = "12 Wallaby Way")
        @NotBlank(message = "street address is required")
        @Size(max = 200, message = "street address must be at most 200 characters")
        String street,

        @Schema(example = "Sydney")
        @NotBlank(message = "suburb is required")
        @Size(max = 100, message = "suburb must be at most 100 characters")
        String suburb,

        @Schema(example = "NSW")
        @NotNull(message = "state is required")
        AustralianState state,

        @Schema(example = "2000")
        @NotBlank(message = "postcode is required")
        @Pattern(regexp = "\\d{4}", message = "postcode must be exactly 4 digits")
        String postcode

) implements PostcodeMatchesStateValidator.StateAndPostcode {
}
