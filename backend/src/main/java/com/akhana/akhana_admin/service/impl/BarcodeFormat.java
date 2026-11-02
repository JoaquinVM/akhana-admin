package com.akhana.akhana_admin.service.impl;

import java.util.Optional;

/** CAT-10: what a factory barcode looks like: EAN-8, UPC-A or EAN-13 with a valid check digit. */
public final class BarcodeFormat {

    private BarcodeFormat() {
    }

    /** The form it is stored and compared in (UPC-A with a leading 0), or empty when it is not a valid barcode. */
    public static Optional<String> canonicalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String digits = raw.trim();
        if (!digits.matches("[0-9]{8}|[0-9]{12}|[0-9]{13}")) {
            return Optional.empty();
        }
        String canonical = digits.length() == 12 ? "0" + digits : digits;
        return hasValidCheckDigit(canonical) ? Optional.of(canonical) : Optional.empty();
    }

    // From the right, without the check digit, the weights alternate 3 and 1
    private static boolean hasValidCheckDigit(String digits) {
        int sum = 0;
        int last = digits.length() - 1;
        for (int i = 0; i < last; i++) {
            int weight = (last - i) % 2 == 1 ? 3 : 1;
            sum += (digits.charAt(i) - '0') * weight;
        }
        return (10 - sum % 10) % 10 == digits.charAt(last) - '0';
    }
}
