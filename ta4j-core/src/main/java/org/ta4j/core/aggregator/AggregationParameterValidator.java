/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.aggregator;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Validation helpers for aggregator constructor parameters.
 */
final class AggregationParameterValidator {

    private AggregationParameterValidator() {
    }

    static Number requirePositive(Number value, String parameterName) {
        Objects.requireNonNull(value, parameterName);
        ensureFinite(value, parameterName);
        BigDecimal decimal = parseDecimal(value, parameterName);
        if (decimal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(parameterName + " must be greater than zero.");
        }
        return value;
    }

    private static BigDecimal parseDecimal(Number value, String parameterName) {
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    parameterName + " must be a finite numeric value representable as decimal.", ex);
        }
    }

    private static void ensureFinite(Number value, String parameterName) {
        if (value instanceof Double d && !Double.isFinite(d)) {
            throw new IllegalArgumentException(parameterName + " must be finite.");
        }
        if (value instanceof Float f && !Float.isFinite(f)) {
            throw new IllegalArgumentException(parameterName + " must be finite.");
        }
    }
}
