package com.xtramile.patient.validation;

import java.util.regex.Pattern;

/**
 * Normalisation and validation for Australian phone numbers.
 *
 * <p>Users type the same number in many shapes: {@code 0412 345 678},
 * {@code (02) 9876-5432}, {@code +61 412 345 678}. Storing those verbatim makes exact match
 * search and de-duplication impossible, so every number is canonicalised to E.164
 * ({@code +61XXXXXXXXX}) on the way in and rendered back in a readable local format on the way
 * out.
 */
public final class PhoneNumbers {

    /** National significant number: area/mobile prefix 2,3,4,7 or 8 followed by eight digits. */
    private static final Pattern NSN = Pattern.compile("^[23478]\\d{8}$");

    private static final Pattern NON_DIALLABLE = Pattern.compile("[\\s()\\-.]");

    private PhoneNumbers() {
    }

    /**
     * Canonicalises a user supplied Australian number to E.164.
     *
     * @return the number as {@code +61XXXXXXXXX}, or {@code null} when it cannot be interpreted
     *         as a valid Australian fixed line or mobile number
     */
    public static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = NON_DIALLABLE.matcher(raw).replaceAll("");
        if (digits.isEmpty()) {
            return null;
        }
        String nsn;
        if (digits.startsWith("+61")) {
            nsn = digits.substring(3);
        } else if (digits.startsWith("0061")) {
            nsn = digits.substring(4);
        } else if (digits.startsWith("61") && digits.length() == 11) {
            nsn = digits.substring(2);
        } else if (digits.startsWith("0")) {
            nsn = digits.substring(1);
        } else {
            nsn = digits;
        }
        return NSN.matcher(nsn).matches() ? "+61" + nsn : null;
    }

    public static boolean isValid(String raw) {
        return normalise(raw) != null;
    }

    /**
     * Renders a stored E.164 number in the local format Australians expect:
     * {@code 0412 345 678} for mobiles, {@code (02) 9876 5432} for fixed lines.
     */
    public static String toLocalFormat(String e164) {
        if (e164 == null || !e164.startsWith("+61") || e164.length() != 12) {
            return e164;
        }
        String nsn = e164.substring(3);
        if (nsn.startsWith("4")) {
            return "0%s %s %s".formatted(nsn.substring(0, 3), nsn.substring(3, 6), nsn.substring(6));
        }
        return "(0%s) %s %s".formatted(nsn.charAt(0), nsn.substring(1, 5), nsn.substring(5));
    }
}
