package com.xtramile.patient.domain;

import java.util.List;

/**
 * Australian states and territories, each carrying the Australia Post postcode ranges
 * that are valid for it.
 *
 * <p>Keeping the ranges on the enum (rather than in a validator {@code switch}) means the
 * reference data lives in exactly one place and the validator stays trivial.
 *
 * <p>Ranges are the standard Australia Post allocations. They intentionally cover only
 * physical-delivery postcodes; PO-Box-only and LVR (large volume receiver) ranges share the
 * same blocks and are therefore accepted as well.
 */
public enum AustralianState {

    ACT("Australian Capital Territory", range(200, 299), range(2600, 2618), range(2900, 2920)),
    NSW("New South Wales", range(1000, 1999), range(2000, 2599), range(2619, 2899), range(2921, 2999)),
    NT("Northern Territory", range(800, 899), range(900, 999)),
    QLD("Queensland", range(4000, 4999), range(9000, 9999)),
    SA("South Australia", range(5000, 5799), range(5800, 5999)),
    TAS("Tasmania", range(7000, 7799), range(7800, 7999)),
    VIC("Victoria", range(3000, 3999), range(8000, 8999)),
    WA("Western Australia", range(6000, 6797), range(6800, 6999));

    private final String displayName;
    private final List<PostcodeRange> postcodeRanges;

    AustralianState(String displayName, PostcodeRange... ranges) {
        this.displayName = displayName;
        this.postcodeRanges = List.of(ranges);
    }

    public String displayName() {
        return displayName;
    }

    public List<PostcodeRange> postcodeRanges() {
        return postcodeRanges;
    }

    /**
     * @param postcode a four digit postcode, already validated for format
     * @return true when the postcode falls inside one of this state's allocated ranges
     */
    public boolean accepts(int postcode) {
        return postcodeRanges.stream().anyMatch(r -> r.contains(postcode));
    }

    private static PostcodeRange range(int from, int to) {
        return new PostcodeRange(from, to);
    }

    /** An inclusive postcode range such as 3000-3999 for VIC. */
    public record PostcodeRange(int from, int to) {
        public boolean contains(int postcode) {
            return postcode >= from && postcode <= to;
        }

        @Override
        public String toString() {
            return "%04d-%04d".formatted(from, to);
        }
    }
}
