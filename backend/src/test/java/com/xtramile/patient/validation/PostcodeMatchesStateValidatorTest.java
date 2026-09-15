package com.xtramile.patient.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.xtramile.patient.domain.AustralianState;
import com.xtramile.patient.web.dto.AddressRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Exercises the cross-field postcode/state constraint through the real Bean Validation engine,
 * so the annotation wiring is tested and not just the validator class.
 */
@DisplayName("PostcodeMatchesState")
class PostcodeMatchesStateValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static AddressRequest address(AustralianState state, String postcode) {
        return new AddressRequest("12 Wallaby Way", "Somewhere", state, postcode);
    }

    @ParameterizedTest(name = "{1} is a valid {0} postcode")
    @CsvSource({
            "NSW, 2000", "NSW, 1234", "NSW, 2999",
            "VIC, 3000", "VIC, 3999", "VIC, 8001",
            "QLD, 4000", "QLD, 9001",
            "SA,  5000", "SA,  5999",
            "WA,  6000", "WA,  6999",
            "TAS, 7000", "TAS, 7999",
            "ACT, 2600", "ACT, 0200", "ACT, 2911",
            "NT,  0800", "NT,  0900",
    })
    void acceptsMatchingPostcodes(AustralianState state, String postcode) {
        assertThat(validator.validate(address(state, postcode))).isEmpty();
    }

    @ParameterizedTest(name = "{1} is not a valid {0} postcode")
    @CsvSource({
            "VIC, 2000",  // Sydney postcode with Victoria selected - the classic mistake
            "NSW, 3000",
            "QLD, 6000",
            "WA,  4000",
            "TAS, 5000",
            "NT,  2000",
            "ACT, 2650",  // inside the NSW gap between the two ACT blocks
            "SA,  6000",
    })
    void rejectsMismatchedPostcodes(AustralianState state, String postcode) {
        var violations = validator.validate(address(state, postcode));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .as("the violation must point at the postcode field so the UI can highlight it")
                .isEqualTo("postcode");
        assertThat(violations.iterator().next().getMessage())
                .contains(postcode)
                .contains(state.name());
    }

    @ParameterizedTest
    @EnumSource(AustralianState.class)
    @DisplayName("every state declares at least one postcode range")
    void everyStateHasRanges(AustralianState state) {
        assertThat(state.postcodeRanges()).isNotEmpty();
        assertThat(state.displayName()).isNotBlank();
    }

    @Test
    @DisplayName("postcode ranges do not overlap between states, so a postcode never has two owners")
    void rangesDoNotOverlap() {
        record Owned(int postcode, AustralianState state) {
        }

        Set<Integer> seen = new java.util.HashSet<>();
        for (AustralianState state : AustralianState.values()) {
            for (var range : state.postcodeRanges()) {
                for (int p = range.from(); p <= range.to(); p++) {
                    assertThat(seen.add(p))
                            .as("postcode %04d is claimed by more than one state (%s)", p, state)
                            .isTrue();
                }
            }
        }
        assertThat(seen).isNotEmpty();
    }

    @Test
    @DisplayName("a malformed postcode is left to @Pattern, not double reported here")
    void doesNotDoubleReportFormatErrors() {
        var violations = validator.validate(address(AustralianState.NSW, "20"));

        assertThat(violations.stream().map(v -> v.getMessage()).collect(Collectors.toSet()))
                .allMatch(m -> m.contains("4 digits"));
    }

    @Test
    @DisplayName("a blank postcode reports 'required' once, not a range mismatch as well")
    void blankPostcodeReportsRequiredOnly() {
        var violations = validator.validate(address(AustralianState.NSW, ""));

        assertThat(violations).hasSize(2); // @NotBlank and @Pattern; not the range constraint
        assertThat(violations).noneMatch(v -> v.getMessage().contains("not allocated"));
    }
}
