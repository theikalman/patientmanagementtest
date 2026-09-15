package com.xtramile.patient.service.exception;

import java.util.Collection;

/**
 * Raised when a client asks to sort by a property that is not on the allow list. Maps to HTTP 400.
 *
 * <p>Without an allow list, an unknown sort property reaches Hibernate and surfaces as an opaque
 * HTTP 500, and sortable properties become an accidental, unversioned part of the public API.
 */
public class InvalidSortPropertyException extends RuntimeException {

    public InvalidSortPropertyException(String property, Collection<String> allowed) {
        super("Cannot sort by '%s'. Sortable properties are: %s".formatted(property, String.join(", ", allowed)));
    }
}
