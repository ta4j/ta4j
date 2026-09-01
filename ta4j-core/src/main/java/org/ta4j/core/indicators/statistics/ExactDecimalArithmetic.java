/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.math.BigDecimal;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Exact decimal arithmetic shared by the subnormal-magnitude recovery paths of
 * {@link CusumIndicator} and {@link EwmaVarianceIndicator}.
 *
 * <p>
 * The difference-form EWMA-style update
 * {@code previous + (1 - decay) * (current - previous)} computes its product
 * with intermediate rounding. At the lower active primitive grid, a
 * subnormal-magnitude result or update difference can move a later
 * normal-magnitude result by one grid ulp. On the double grid, the ulp of a
 * subnormal is {@code Double.MIN_VALUE} itself, so the accumulated error can
 * move an exact tie to the wrong side: {@code 0.5 * MIN_VALUE} collapses to
 * zero and {@code 1.5 * MIN_VALUE} publishes {@code MIN_VALUE} although the
 * correctly rounded single-rounding results are {@code 0} and
 * {@code 2 * MIN_VALUE}. The recovery paths also cover one active-grid ulp
 * above the minimum-normal boundary, where an intermediate product can misround
 * a correctly minimum-normal result.
 *
 * <p>
 * The exact operands are {@link BigDecimal} expansions: {@link Float} and
 * {@link Double} delegates carry exact binary values and
 * {@link org.ta4j.core.num.DecimalNum} carries an exact decimal that may lie
 * beyond the double range. Exact operand expansion must never go through
 * {@link Num#doubleValue()}; only subnormal-grid classification deliberately
 * consults a primitive delegate.
 */
final class ExactDecimalArithmetic {

    private ExactDecimalArithmetic() {
        // Utility class.
    }

    /**
     * Tests the active primitive grid to identify subnormal magnitude, the
     * minimum-normal boundary, and its immediately adjacent normal value: a
     * {@link Float} delegate uses the float grid, while all other values retain the
     * double-grid classification. Zero is included; non-finite and NaN values are
     * excluded. This is a recovery classification rather than validation, so finite
     * decimal values beyond the double range simply return {@code false}.
     *
     * @param value the value to classify
     * @return {@code true} when the magnitude is subnormal, minimum normal, or one
     *         active-grid ulp above minimum normal
     */
    static boolean isSubnormalMagnitude(Num value) {
        if (value.getDelegate() instanceof Float floatValue) {
            return Math.abs(floatValue) <= Math.nextUp(Float.MIN_NORMAL);
        }
        return Math.abs(value.doubleValue()) <= Math.nextUp(Double.MIN_NORMAL);
    }

    /**
     * Tests a difference-form update for low-magnitude active-grid arithmetic that
     * requires an exactly recombined fallback. A finite low-magnitude difference
     * can perturb a normal result by one grid ulp, so checking only the already
     * rounded result is insufficient.
     *
     * @param result       the rounded difference-form result
     * @param intermediate the finite difference-form intermediate
     * @return {@code true} when either operand needs exact recovery
     */
    static boolean requiresExactRecovery(Num result, Num intermediate) {
        return isSubnormalMagnitude(result) || isSubnormalMagnitude(intermediate);
    }

    /**
     * Exact expansion of {@code value} as a {@link BigDecimal}: the exact binary
     * value for a {@link Float} or {@link Double} delegate, the exact decimal
     * expansion for any other {@link Num} implementation.
     *
     * @param value the finite value to expand
     * @return the exact decimal expansion
     */
    static BigDecimal exactValueOf(Num value) {
        Number delegate = value.getDelegate();
        if (delegate instanceof Double || delegate instanceof Float) {
            return new BigDecimal(delegate.doubleValue());
        }
        return value.bigDecimalValue();
    }

    /**
     * The convex combination {@code first * (1 - secondWeight) + second *
     * secondWeight}, combined without intermediate rounding and narrowed once
     * through {@code factory}. The first exact weight is derived from the supplied,
     * actually applied second weight: independently narrowed complementary
     * {@link Num} values need not sum to one in a coarse factory.
     *
     * @param factory      the factory that narrows the exact sum
     * @param first        the exact expansion of the first operand
     * @param second       the exact expansion of the second operand
     * @param secondWeight the actually applied second convex weight
     * @return the exactly combined, once-narrowed weighted sum
     */
    static Num exactWeightedSum(NumFactory factory, BigDecimal first, BigDecimal second, Num secondWeight) {
        BigDecimal exactSecondWeight = exactValueOf(secondWeight);
        BigDecimal exactFirstWeight = BigDecimal.ONE.subtract(exactSecondWeight);
        return factory.numOf(first.multiply(exactFirstWeight).add(second.multiply(exactSecondWeight)));
    }
}
