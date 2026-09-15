package com.xtramile.patient.service.exception;

/**
 * Raised when an update carries a {@code version} older than the one currently stored, meaning
 * somebody else has changed the record since this client read it. Maps to HTTP 409.
 */
public class StalePatientDataException extends RuntimeException {

    private final long expectedVersion;
    private final long actualVersion;

    public StalePatientDataException(Long id, long expectedVersion, long actualVersion) {
        super(("Patient %d has been modified by another user: you are editing version %d "
                + "but the current version is %d. Reload the patient and re-apply your changes.")
                .formatted(id, expectedVersion, actualVersion));
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public long getExpectedVersion() {
        return expectedVersion;
    }

    public long getActualVersion() {
        return actualVersion;
    }
}
