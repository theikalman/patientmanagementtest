package com.xtramile.patient.web;

import com.xtramile.patient.service.exception.InvalidSortPropertyException;
import com.xtramile.patient.service.exception.PatientNotFoundException;
import com.xtramile.patient.service.exception.StalePatientDataException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns exceptions into RFC 9457 {@code application/problem+json} responses.
 *
 * <p>Every error the API can produce is described in exactly one place, which means:
 * <ul>
 *   <li>the Angular client can rely on a single error shape and render {@code detail} directly;</li>
 *   <li>internal details (stack traces, SQL, class names) never leak to the caller;</li>
 *   <li>controllers and services throw meaningful domain exceptions instead of assembling
 *       {@code ResponseEntity} objects.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String PROBLEM_BASE = "https://api.xtramile.com/problems/";

    /** One rejected field, in a shape the Angular reactive form can map straight onto a control. */
    public record FieldViolation(String field, String message, Object rejectedValue) {
    }

    @ExceptionHandler(PatientNotFoundException.class)
    public ProblemDetail handleNotFound(PatientNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Patient not found", ex.getMessage(), "patient-not-found", request);
    }

    /**
     * Bean Validation failures on a {@code @RequestBody}. The per field list matters: a UI that
     * can only show "validation failed" forces the user to guess which input is wrong.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage(), safeRejectedValue(error)))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "The request contains %d invalid field(s). See 'errors' for details.".formatted(violations.size()),
                "validation-failed",
                request);
        problem.setProperty("errors", violations);
        return problem;
    }

    /**
     * Concurrent edit. 409 rather than 412, because the client sends the expected version in the
     * body as part of the resource representation, not as an HTTP precondition header.
     */
    @ExceptionHandler(StalePatientDataException.class)
    public ProblemDetail handleStaleData(StalePatientDataException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT, "Concurrent modification", ex.getMessage(), "stale-data", request);
        problem.setProperty("expectedVersion", ex.getExpectedVersion());
        problem.setProperty("actualVersion", ex.getActualVersion());
        return problem;
    }

    /** The narrow race the explicit version check cannot close: someone committed between our read and flush. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic lock failure on {}", request.getRequestURI(), ex);
        return problem(
                HttpStatus.CONFLICT,
                "Concurrent modification",
                "This patient was modified by another user while your change was being saved. "
                        + "Reload the patient and try again.",
                "stale-data",
                request);
    }

    @ExceptionHandler(InvalidSortPropertyException.class)
    public ProblemDetail handleInvalidSort(InvalidSortPropertyException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid sort", ex.getMessage(), "invalid-sort", request);
    }

    /** Malformed JSON, or a value that cannot be bound - for example gender "ROBOT" or a bad date. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Malformed request body",
                "The request body could not be parsed. Check that the JSON is well formed and that "
                        + "enum and date values are valid.",
                "malformed-body",
                request);
    }

    /** A path variable or query parameter of the wrong type, for example /patients/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid parameter",
                "Parameter '%s' has an invalid value: %s".formatted(ex.getName(), ex.getValue()),
                "invalid-parameter",
                request);
    }

    /** Unique or not-null constraint violations that slipped past validation. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}", request.getRequestURI(), ex);
        return problem(
                HttpStatus.CONFLICT,
                "Data conflict",
                "The request conflicts with data that already exists.",
                "data-conflict",
                request);
    }

    /**
     * Last resort. The exception is logged in full for operators, while the caller gets a generic
     * message - an unexpected failure must never become an information disclosure.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "The request could not be completed. Please contact support if this persists.",
                "internal-error",
                request);
    }

    private ProblemDetail problem(
            HttpStatus status, String title, String detail, String type, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_BASE + type));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Never echo a password-like value back; for this API the risk is low but the habit matters. */
    private static Object safeRejectedValue(FieldError error) {
        Object value = error.getRejectedValue();
        return (value instanceof CharSequence cs && cs.length() > 100) ? "<omitted>" : value;
    }
}
