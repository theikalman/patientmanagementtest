package com.xtramile.patient.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/**
 * Australian residential address, modelled as a JPA {@link Embeddable}.
 *
 * <p>An address has no identity of its own in this domain: it is always owned by exactly one
 * patient and is replaced wholesale when it changes. Embedding it keeps the address columns on
 * the {@code patient} table, which avoids a join on every read of the patient grid while still
 * giving us a cohesive value object in Java.
 */
@Embeddable
public class AustralianAddress {

    @Column(name = "street", nullable = false, length = 200)
    private String street;

    @Column(name = "suburb", nullable = false, length = 100)
    private String suburb;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 3)
    private AustralianState state;

    @Column(name = "postcode", nullable = false, length = 4)
    private String postcode;

    protected AustralianAddress() {
        // required by JPA
    }

    public AustralianAddress(String street, String suburb, AustralianState state, String postcode) {
        this.street = street;
        this.suburb = suburb;
        this.state = state;
        this.postcode = postcode;
    }

    public String getStreet() {
        return street;
    }

    public String getSuburb() {
        return suburb;
    }

    public AustralianState getState() {
        return state;
    }

    public String getPostcode() {
        return postcode;
    }

    /** Value objects compare by value, not by reference. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AustralianAddress other)) {
            return false;
        }
        return Objects.equals(street, other.street)
                && Objects.equals(suburb, other.suburb)
                && state == other.state
                && Objects.equals(postcode, other.postcode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(street, suburb, state, postcode);
    }

    @Override
    public String toString() {
        return "%s, %s %s %s".formatted(street, suburb, state, postcode);
    }
}
