package com.xtramile.patient.domain;

/**
 * Administrative gender.
 *
 * <p>The value set is deliberately aligned with the HL7 FHIR {@code administrative-gender}
 * value set (male | female | other | unknown) so that this service can be mapped onto a
 * clinical interoperability layer later without a data migration.
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER,
    UNKNOWN
}
