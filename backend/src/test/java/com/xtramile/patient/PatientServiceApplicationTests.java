package com.xtramile.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.xtramile.patient.service.PatientService;
import com.xtramile.patient.service.PidGenerator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Context smoke test.
 *
 * <p>Worth keeping beyond "the context loads": it also asserts that Flyway ran and that
 * Hibernate's {@code ddl-auto: validate} agreed with the migrated schema, which is the check that
 * catches an entity/migration mismatch at build time rather than on deployment.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Application context")
class PatientServiceApplicationTests {

    @Autowired
    private PatientService patientService;

    @Autowired
    private PidGenerator pidGenerator;

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("loads with the service layer wired")
    void contextLoads() {
        assertThat(patientService).isNotNull();
    }

    @Test
    @DisplayName("Flyway has applied the schema migration, and no fixture migrations leaked in")
    void flywayHasRun() {
        var applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        assertThat(applied).allSatisfy(m -> assertThat(m.getState().isFailed()).isFalse());
        // The V900 demo fixtures live in db/seed, which the test profile does not load.
        assertThat(applied).noneSatisfy(m -> assertThat(m.getVersion().toString()).isEqualTo("900"));
    }

    @Test
    @DisplayName("the PID generator resolves a database specific incrementer and issues formatted PIDs")
    void pidGeneratorIssuesFormattedIds() {
        String first = pidGenerator.next();
        String second = pidGenerator.next();

        assertThat(first).matches("PAT-\\d{6}");
        assertThat(second).isNotEqualTo(first);
    }
}
