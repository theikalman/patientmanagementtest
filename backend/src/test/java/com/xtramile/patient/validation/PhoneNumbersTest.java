package com.xtramile.patient.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PhoneNumbers")
class PhoneNumbersTest {

    @ParameterizedTest(name = "\"{0}\" normalises to {1}")
    @CsvSource({
            "0412345678,       +61412345678",
            "0412 345 678,     +61412345678",
            "0412-345-678,     +61412345678",
            "(02) 9876 5432,   +61298765432",
            "+61 412 345 678,  +61412345678",
            "+61412345678,     +61412345678",
            "0061412345678,    +61412345678",
            "61412345678,      +61412345678",
            "  0412345678  ,   +61412345678",
            "03 9123 4567,     +61391234567",
            "07 3123 4567,     +61731234567",
            "08 8123 4567,     +61881234567",
    })
    @DisplayName("every shape an Australian number is typed in collapses to one canonical value")
    void normalisesToE164(String input, String expected) {
        assertThat(PhoneNumbers.normalise(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" is rejected")
    @ValueSource(strings = {
            "12345",            // too short
            "0412345",          // too short
            "041234567890",     // too long
            "0112345678",       // 01 is not an allocated Australian prefix
            "0512345678",       // 05 is not an allocated Australian prefix
            "1300123456",       // service number, not a patient contact number
            "+1 555 123 4567",  // not Australian
            "abcdefghij",
    })
    void rejectsInvalidNumbers(String input) {
        assertThat(PhoneNumbers.normalise(input)).isNull();
        assertThat(PhoneNumbers.isValid(input)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null and empty are not valid numbers, but never throw")
    void handlesNullAndEmpty(String input) {
        assertThat(PhoneNumbers.normalise(input)).isNull();
    }

    @ParameterizedTest(name = "{0} renders as {1}")
    @CsvSource({
            "+61412345678, 0412 345 678",
            "+61298765432, (02) 9876 5432",
            "+61391234567, (03) 9123 4567",
    })
    @DisplayName("stored numbers are rendered back in the format Australians read")
    void formatsForDisplay(String stored, String expected) {
        assertThat(PhoneNumbers.toLocalFormat(stored)).isEqualTo(expected);
    }

    @Test
    @DisplayName("normalisation is idempotent, so re-saving a patient cannot corrupt the number")
    void isIdempotent() {
        String once = PhoneNumbers.normalise("0412 345 678");

        assertThat(PhoneNumbers.normalise(once)).isEqualTo(once);
    }

    @Test
    @DisplayName("a value that is not a stored E.164 number is passed through unchanged")
    void formatLeavesUnknownValuesAlone() {
        assertThat(PhoneNumbers.toLocalFormat("not-a-number")).isEqualTo("not-a-number");
        assertThat(PhoneNumbers.toLocalFormat(null)).isNull();
    }
}
