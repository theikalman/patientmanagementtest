package com.xtramile.patient.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross origin configuration.
 *
 * <p>Two browser origins other than this service need access during development:
 * <ul>
 *   <li>the Angular dev server on :4200, which calls {@code /api/**};</li>
 *   <li>the standalone Swagger UI container on :8081 (see {@code docker-compose.yml}), which
 *       fetches the OpenAPI document and then issues "Try it out" calls against {@code /api/**}.</li>
 * </ul>
 *
 * <p>The OpenAPI document therefore has to be part of the CORS mapping too. Mapping only
 * {@code /api/**} is the mistake that makes a containerised Swagger UI show an empty
 * "Failed to load API definition" with no useful detail, because the browser blocks the fetch
 * before the response is ever read.
 *
 * <p>The allowed origins come from configuration rather than being hard coded, so a deployment
 * can point at its real front end host. In production the two applications are normally served
 * from the same origin (the Angular bundle is served by this service, or both sit behind one
 * gateway) and the list can then be empty, which switches CORS off entirely.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;
    private final String apiDocsPath;

    public WebConfig(
            @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins,
            // Read from springdoc's own property so the two cannot drift apart if the
            // document is ever relocated.
            @Value("${springdoc.api-docs.path:/v3/api-docs}") String apiDocsPath) {
        this.allowedOrigins = allowedOrigins;
        this.apiDocsPath = apiDocsPath;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isEmpty()) {
            return;
        }
        String[] origins = allowedOrigins.toArray(String[]::new);

        // The API itself.
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        // The OpenAPI document. Read only, so only GET is allowed: there is nothing to POST
        // here, and a narrower rule is one less thing to reason about later.
        // Both the document itself and anything under it (grouped definitions,
        // /swagger-config) are covered.
        registry.addMapping(apiDocsPath)
                .allowedOrigins(origins)
                .allowedMethods("GET", "OPTIONS")
                .maxAge(3600);

        registry.addMapping(apiDocsPath + "/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "OPTIONS")
                .maxAge(3600);
    }
}
