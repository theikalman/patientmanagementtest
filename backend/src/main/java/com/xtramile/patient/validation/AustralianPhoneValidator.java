package com.xtramile.patient.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AustralianPhoneValidator implements ConstraintValidator<AustralianPhone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null and blank are the business of @NotBlank; a format constraint should not
        // double report them.
        if (value == null || value.isBlank()) {
            return true;
        }
        return PhoneNumbers.isValid(value);
    }
}
