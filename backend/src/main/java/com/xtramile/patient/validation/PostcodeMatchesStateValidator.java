package com.xtramile.patient.validation;

import com.xtramile.patient.domain.AustralianState;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates the {@link PostcodeMatchesState} constraint against any object that can expose a
 * state and a postcode, so the same rule can be reused by other address shaped DTOs.
 */
public class PostcodeMatchesStateValidator
        implements ConstraintValidator<PostcodeMatchesState, PostcodeMatchesStateValidator.StateAndPostcode> {

    /** Minimal contract an address DTO implements to opt into this constraint. */
    public interface StateAndPostcode {
        AustralianState state();

        String postcode();
    }

    @Override
    public boolean isValid(StateAndPostcode value, ConstraintValidatorContext context) {
        if (value == null || value.state() == null || value.postcode() == null) {
            // Presence is enforced by @NotNull/@NotBlank on the individual fields.
            return true;
        }
        // Anything that is not exactly four digits is @Pattern's business. Reporting a range
        // violation as well would show the user two errors for one mistake.
        if (!value.postcode().matches("\\d{4}")) {
            return true;
        }
        int postcode = Integer.parseInt(value.postcode());
        if (value.state().accepts(postcode)) {
            return true;
        }
        // Report against the postcode field so the Angular form can highlight the right input.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        "postcode %s is not allocated to %s (valid ranges: %s)"
                                .formatted(value.postcode(), value.state(), value.state().postcodeRanges()))
                .addPropertyNode("postcode")
                .addConstraintViolation();
        return false;
    }
}
