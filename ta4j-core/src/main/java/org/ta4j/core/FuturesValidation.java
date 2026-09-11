/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.ta4j.core.num.Num;

/**
 * Shared argument checks for native futures contract, fee, event and snapshot
 * values.
 *
 * <p>
 * Package-private: these checks carry no behaviour beyond validating explicit
 * inputs and are not part of the public API.
 * </p>
 *
 * @since 0.25.1
 */
final class FuturesValidation {

    private FuturesValidation() {
    }

    static <T> T requireNonNull(T value, String name) {
        return Objects.requireNonNull(value, name);
    }

    static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static Instant requireNonNull(Instant value, String name) {
        return Objects.requireNonNull(value, name);
    }

    static String requireNonBlankOrNull(String value, String name) {
        if (value == null) {
            return null;
        }
        return requireNonBlank(value, name);
    }

    static Num requireFinite(Num value, String name) {
        Objects.requireNonNull(value, name);
        if (!Num.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    static Num requireFiniteOrNull(Num value, String name) {
        if (value == null) {
            return null;
        }
        return requireFinite(value, name);
    }

    static Num requirePositiveFinite(Num value, String name) {
        requireFinite(value, name);
        if (!value.isPositive()) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
        return value;
    }

    static Num requirePositiveFiniteOrNull(Num value, String name) {
        if (value == null) {
            return null;
        }
        return requirePositiveFinite(value, name);
    }

    static Num requireNonNegativeFinite(Num value, String name) {
        requireFinite(value, name);
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must be nonnegative and finite");
        }
        return value;
    }

    static Num requireNonNegativeFiniteOrNull(Num value, String name) {
        if (value == null) {
            return null;
        }
        return requireNonNegativeFinite(value, name);
    }

    static Duration requirePositiveDurationOrNull(Duration value, String name) {
        if (value == null) {
            return null;
        }
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    static void requireNotGreaterThan(Num minimum, Num maximum, String minimumName, String maximumName) {
        if (minimum != null && maximum != null && minimum.isGreaterThan(maximum)) {
            throw new IllegalArgumentException(minimumName + " must not exceed " + maximumName);
        }
    }

    static int numHash(Num value) {
        if (value == null) {
            return 0;
        }
        if (value.isNaN()) {
            return 31;
        }
        return value.bigDecimalValue().stripTrailingZeros().hashCode();
    }

    static boolean numEqualsNullable(Num left, Num right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left == right || left.isEqual(right);
    }
}
