package com.xtramile.patient.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class level constraint asserting that an address' postcode falls inside a range that
 * Australia Post actually allocates to its state.
 *
 * <p>This catches the single most common data entry error in Australian address forms - picking
 * the wrong state from the dropdown - which a plain {@code \\d{4}} pattern cannot.
 */
@Documented
@Constraint(validatedBy = PostcodeMatchesStateValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PostcodeMatchesState {

    String message() default "postcode is not valid for the selected state";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
