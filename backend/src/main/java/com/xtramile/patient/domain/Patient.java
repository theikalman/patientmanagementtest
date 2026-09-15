package com.xtramile.patient.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A patient record.
 *
 * <p>Two identifiers are deliberately kept apart:
 * <ul>
 *   <li>{@code id} - a surrogate, database generated primary key. It is an implementation
 *       detail used for joins and for addressing rows over HTTP.</li>
 *   <li>{@code pid} - the business "Patient Identity". It is human readable, allocated from a
 *       dedicated database sequence, unique, and immutable once assigned.</li>
 * </ul>
 * Mixing the two (for instance exposing the surrogate key as the PID) would leak row counts
 * and would make it impossible to migrate patients between databases without renumbering them.
 *
 * <p>The entity is mutable through intention revealing methods rather than blanket setters, so
 * that invariants such as "the PID never changes" are enforced by the type itself and not only
 * by the service layer.
 */
@Entity
@Table(name = "patient")
@EntityListeners(AuditingEntityListener.class)
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pid", nullable = false, unique = true, updatable = false, length = 20)
    private String pid;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 10)
    private Gender gender;

    /** Stored in normalised E.164 form, for example {@code +61412345678}. */
    @Column(name = "phone_no", nullable = false, length = 20)
    private String phoneNo;

    @Embedded
    private AustralianAddress address;

    /**
     * Optimistic locking token. Two users editing the same patient concurrently would
     * otherwise silently overwrite each other (the "lost update" problem); with this column the
     * second write fails and the service translates it into HTTP 409.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Patient() {
        // required by JPA
    }

    public Patient(String pid,
                   String firstName,
                   String lastName,
                   LocalDate dateOfBirth,
                   Gender gender,
                   String phoneNo,
                   AustralianAddress address) {
        this.pid = Objects.requireNonNull(pid, "pid");
        applyDemographics(firstName, lastName, dateOfBirth, gender, phoneNo, address);
    }

    /**
     * Replaces every client editable attribute in one call. Keeping this as a single operation
     * makes it impossible to leave the entity half updated if a later field fails validation.
     */
    public void applyDemographics(String firstName,
                                  String lastName,
                                  LocalDate dateOfBirth,
                                  Gender gender,
                                  String phoneNo,
                                  AustralianAddress address) {
        this.firstName = Objects.requireNonNull(firstName, "firstName");
        this.lastName = Objects.requireNonNull(lastName, "lastName");
        this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        this.gender = Objects.requireNonNull(gender, "gender");
        this.phoneNo = Objects.requireNonNull(phoneNo, "phoneNo");
        this.address = Objects.requireNonNull(address, "address");
    }

    public Long getId() {
        return id;
    }

    public String getPid() {
        return pid;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    /** Derived rather than stored, so it can never drift out of date. */
    public int getAge(LocalDate today) {
        return Period.between(dateOfBirth, today).getYears();
    }

    public Gender getGender() {
        return gender;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public AustralianAddress getAddress() {
        return address;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Identity is the business key, not the surrogate key: a transient Patient that has not been
     * persisted yet still has a PID, so equality stays stable across a persist.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Patient other)) {
            return false;
        }
        return pid != null && pid.equals(other.pid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(pid);
    }

    @Override
    public String toString() {
        return "Patient[pid=%s, name=%s]".formatted(pid, getFullName());
    }
}
