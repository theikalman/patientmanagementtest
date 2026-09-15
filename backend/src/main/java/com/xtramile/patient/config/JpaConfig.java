package com.xtramile.patient.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data auditing, which populates {@code createdAt} / {@code updatedAt} without any
 * entity listener code of our own.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {

    /**
     * A single injectable {@link Clock}. Injecting the clock instead of calling
     * {@code LocalDate.now()} in place is what lets the age calculation be asserted in a unit test
     * without the test becoming time dependent.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
