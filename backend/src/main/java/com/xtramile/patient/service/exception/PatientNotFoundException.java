package com.xtramile.patient.service.exception;

/** Raised when a patient is addressed by an id or PID that does not exist. Maps to HTTP 404. */
public class PatientNotFoundException extends RuntimeException {

    public static PatientNotFoundException byId(Long id) {
        return new PatientNotFoundException("No patient exists with id " + id);
    }

    public static PatientNotFoundException byPid(String pid) {
        return new PatientNotFoundException("No patient exists with PID " + pid);
    }

    public PatientNotFoundException(String message) {
        super(message);
    }
}
