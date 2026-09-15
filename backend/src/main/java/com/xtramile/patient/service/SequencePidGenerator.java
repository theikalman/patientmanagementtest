package com.xtramile.patient.service;

import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;
import org.springframework.stereotype.Component;

/**
 * Allocates PIDs from a dedicated database sequence.
 *
 * <p>A sequence is used rather than {@code MAX(pid) + 1} or a UUID because it is:
 * <ul>
 *   <li><b>safe under concurrency</b> - the database, not the application, serialises allocation,
 *       so two simultaneous creates can never receive the same PID even across several app
 *       instances;</li>
 *   <li><b>non blocking</b> - unlike a {@code SELECT ... FOR UPDATE} on a counter table it does
 *       not hold a row lock for the length of the transaction;</li>
 *   <li><b>human readable</b> - {@code PAT-000042} can be read out over the phone, which a UUID
 *       cannot.</li>
 * </ul>
 *
 * <p>Sequences are not transactional: a rolled back create burns a PID. That is intentional -
 * gap free numbering would require serialising every insert, and PIDs only have to be unique,
 * not contiguous.
 *
 * <p>The underlying {@link DataFieldMaxValueIncrementer} is chosen per database in
 * {@code PidGeneratorConfig}, so switching from H2 to PostgreSQL changes one bean, not this class.
 */
@Component
public class SequencePidGenerator implements PidGenerator {

    static final String PREFIX = "PAT-";
    static final String FORMAT = PREFIX + "%06d";

    private final DataFieldMaxValueIncrementer incrementer;

    public SequencePidGenerator(DataFieldMaxValueIncrementer incrementer) {
        this.incrementer = incrementer;
    }

    @Override
    public String next() {
        return FORMAT.formatted(incrementer.nextLongValue());
    }
}
