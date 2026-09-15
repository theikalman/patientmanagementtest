package com.xtramile.patient.config;

import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.jdbc.support.incrementer.DataFieldMaxValueIncrementer;
import org.springframework.jdbc.support.incrementer.H2SequenceMaxValueIncrementer;
import org.springframework.jdbc.support.incrementer.PostgresSequenceMaxValueIncrementer;

/**
 * Chooses the database specific way of reading the next value from the PID sequence.
 *
 * <p>Sequence syntax is not portable ({@code NEXT VALUE FOR} on H2, {@code nextval()} on
 * PostgreSQL), so rather than hard coding one dialect into a native query we resolve Spring's
 * {@code DataFieldMaxValueIncrementer} strategy once, at startup, from the JDBC metadata.
 * Supporting another database is then a single extra case here.
 */
@Configuration
public class PidGeneratorConfig {

    /** Created by Flyway in {@code V1__create_patient_table.sql}. */
    public static final String PID_SEQUENCE = "patient_pid_seq";

    @Bean
    public DataFieldMaxValueIncrementer patientPidIncrementer(DataSource dataSource) throws MetaDataAccessException {
        String product = JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getDatabaseProductName);
        return switch (product) {
            case "H2" -> new H2SequenceMaxValueIncrementer(dataSource, PID_SEQUENCE);
            case "PostgreSQL" -> new PostgresSequenceMaxValueIncrementer(dataSource, PID_SEQUENCE);
            default -> throw new IllegalStateException(
                    "No PID sequence strategy configured for database '%s'. Add a case to PidGeneratorConfig."
                            .formatted(product));
        };
    }
}
