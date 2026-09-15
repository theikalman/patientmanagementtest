package com.xtramile.patient.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata for the generated OpenAPI document served at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI patientServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Patient Management API")
                .version("v1")
                .description("""
                        CRUD, search and server side pagination over patient records.

                        Errors are returned as RFC 9457 problem documents
                        (`application/problem+json`). Validation failures additionally carry an
                        `errors` array of `{field, message, rejectedValue}`.
                        """)
                .contact(new Contact().name("Xtramile Solutions Java Engineer Test"))
                .license(new License().name("Provided for assessment purposes")));
    }
}
