package com.xtramile.patient.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The annotated string must be an Australian fixed line or mobile number in any common format. */
@Documented
@Constraint(validatedBy = AustralianPhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface AustralianPhone {

    String message() default
            "must be a valid Australian phone number, for example 0412 345 678 or (02) 9876 5432";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
