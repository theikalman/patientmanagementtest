package com.xtramile.patient.web;

import com.xtramile.patient.domain.AustralianState;
import com.xtramile.patient.domain.Gender;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the enumerations the UI needs to render its dropdowns.
 *
 * <p>Without this, the Angular app would hard code the list of states and genders and the two
 * sides would drift apart the first time a value is added. The server owns the value set; the
 * client renders whatever it is given.
 */
@RestController
@RequestMapping("/api/v1/reference-data")
@Tag(name = "Reference data", description = "Enumerations used to populate form dropdowns")
public class ReferenceDataController {

    /** A dropdown entry: the wire value plus the text a human should see. */
    public record Option(String value, String label) {
    }

    @GetMapping("/genders")
    @Operation(summary = "Administrative gender values (HL7 aligned)")
    public List<Option> genders() {
        return Arrays.stream(Gender.values())
                .map(g -> new Option(g.name(), toTitleCase(g.name())))
                .toList();
    }

    @GetMapping("/states")
    @Operation(summary = "Australian states and territories")
    public List<Option> states() {
        return Arrays.stream(AustralianState.values())
                .map(s -> new Option(s.name(), "%s (%s)".formatted(s.displayName(), s.name())))
                .toList();
    }

    private static String toTitleCase(String value) {
        return value.charAt(0) + value.substring(1).toLowerCase();
    }
}
