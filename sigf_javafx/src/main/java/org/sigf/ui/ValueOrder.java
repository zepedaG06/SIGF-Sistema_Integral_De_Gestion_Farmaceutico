package org.sigf.ui;

import java.math.BigDecimal;

/** Compara valores numéricos y conserva el orden ISO de las fechas sin formato visual. */
final class ValueOrder {
    private ValueOrder() {}

    static int compare(Object left, Object right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        String a = left.toString(), b = right.toString();
        BigDecimal x = numeric(a), y = numeric(b);
        if (x != null && y != null) {
            return x.compareTo(y);
        }
        if (x != null || y != null) {
            return x != null ? -1 : 1;
        }
        return a.compareToIgnoreCase(b);
    }

    private static String number(String value) {
        return value.startsWith("C$ ") ? value.substring(3).replace(",", "") : value;
    }

    private static BigDecimal numeric(String value) {
        try {
            return new BigDecimal(number(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
