package com.xtramile.patient.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross origin configuration.
 *
 * <p>Both ways of running the front end proxy {@code /api} to this service, so the browser sees a
 * single origin and needs no CORS at all. The allow list is a safety net for the cases that are not
 * proxied: an Angular dev server started without {@code proxy.conf.json}, or a browser based API
 * tool pointed straight at this port.
 *
 * <p>The OpenAPI document is mapped as well as the API. Mapping only {@code /api/**} is the easy
 * mistake: a browser based documentation tool then reports "Failed to load API definition" with no
 * useful detail, because the fetch is blocked before any response is read.
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
