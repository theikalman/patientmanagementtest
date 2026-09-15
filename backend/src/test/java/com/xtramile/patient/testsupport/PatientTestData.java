package com.xtramile.patient.testsupport;

import com.xtramile.patient.domain.AustralianAddress;
import com.xtramile.patient.domain.AustralianState;
import com.xtramile.patient.domain.Gender;
import com.xtramile.patient.domain.Patient;
import com.xtramile.patient.web.dto.AddressRequest;
import com.xtramile.patient.web.dto.CreatePatientRequest;
import com.xtramile.patient.web.dto.UpdatePatientRequest;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Object mothers for the tests.
 *
 * <p>Centralising fixture construction keeps each test focused on the one field it is actually
 * about: a test for "phone is normalised" should not also have to spell out a valid address.
 */
public final class PatientTestData {

    public static final LocalDate DOB = LocalDate.of(1985, 4, 12);

    private PatientTestData() {
    }

    public static AustralianAddress address() {
        return new AustralianAddress("12 Wallaby Way", "Sydney", AustralianState.NSW, "2000");
    }

    public static AddressRequest addressRequest() {
        return new AddressRequest("12 Wallaby Way", "Sydney", AustralianState.NSW, "2000");
    }

    public static Patient patient(String pid) {
        return new Patient(pid, "Jane", "Citizen", DOB, Gender.FEMALE, "+61412345678", address());
    }

    /**
     * Builds a Patient that looks like one loaded from the database: id, version and audit
     * timestamps are populated. Those fields have no setters by design (the database owns them),
     * so the test sets them reflectively rather than weakening the entity's API for testing.
     */
    public static Patient persistedPatient(long id, String pid, long version) {
        Patient patient = patient(pid);
        set(patient, "id", id);
        set(patient, "version", version);
        set(patient, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        set(patient, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
        return patient;
    }

    public static CreatePatientRequest createRequest() {
        return new CreatePatientRequest("Jane", "Citizen", DOB, Gender.FEMALE, "0412 345 678", addressRequest());
    }

    public static UpdatePatientRequest updateRequest(long version) {
        return new UpdatePatientRequest(
                "Jane", "Doe", DOB, Gender.FEMALE, "0412 345 678", addressRequest(), version);
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set " + fieldName + " on test fixture", e);
        }
    }
}
