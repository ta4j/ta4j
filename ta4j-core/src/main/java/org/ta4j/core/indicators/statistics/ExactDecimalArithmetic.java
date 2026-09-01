/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.math.BigDecimal;

import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Exact decimal arithmetic shared by the subnormal-magnitude recovery paths of
 * {@link CusumIndicator} and {@link EwmaVarianceIndicator}.
 *
 * <p>
 * The difference-form EWMA-style update
 * {@code previous + (1 - decay) * (current - previous)} computes each convex
 * product with its own intermediate rounding. Each product carries at most half
 * an ulp of error, which a normal-magnitude result absorbs, but a
 * subnormal-magnitude result sits on the coarsest representable grid (the ulp
 * of a subnormal is {@code Double.MIN_VALUE} itself), so the accumulated error
 * can move an exact tie to the wrong side: {@code 0.5 * MIN_VALUE} collapses to
 * zero and {@code 1.5 * MIN_VALUE} publishes {@code MIN_VALUE} although the
 * correctly rounded single-rounding results are {@code 0} and
 * {@code 2 * MIN_VALUE}. The recovery paths detect results of subnormal
 * magnitude and recombine the exact operands without intermediate rounding,
 * narrowing once.
 *
 * <p>
 * The exact operands are {@link BigDecimal} expansions: {@link DoubleNum}
 * carries the exact binary value and {@link org.ta4j.core.num.DecimalNum}
 * carries an exact decimal that may lie beyond the double range. Exact operand
 * expansion must never go through {@link Num#doubleValue()}; only the
 * subnormal-grid classification deliberately uses that primitive projection.
 */
final class ExactDecimalArithmetic {

    private ExactDecimalArithmetic() {
        // Utility class.
    }

    /**
     * Tests the primitive-double projection only to identify the subnormal grid:
     * its absolute value is strictly below the smallest positive normal double
     * (zero included, non-finite and NaN values excluded). This is a classification
     * rather than validation, so finite decimal values beyond the double range
     * simply return {@code false}.
     *
     * @param value the value to classify
     * @return {@code true} when the magnitude is subnormal
     */
    static boolean isSubnormalMagnitude(Num value) {
        return Math.abs(value.doubleValue()) < Double.MIN_NORMAL;
    }

    /**
     * Exact expansion of {@code value} as a {@link BigDecimal}: the exact binary
     * value for {@link DoubleNum}, the exact decimal expansion for any other
     * {@link Num} implementation.
     *
     * @param value the finite value to expand
     * @return the exact decimal expansion
     */
    static BigDecimal exactValueOf(Num value) {
        if (value instanceof DoubleNum) {
            return new BigDecimal(value.doubleValue());
        }
        return value.bigDecimalValue();
    }

    /**
     * The convex combination {@code first * firstWeight + second *
     * secondWeight}, combined without intermediate rounding and narrowed once
     * through {@code factory}. The weights are primitive doubles and are expanded
     * as exact binary values (never through {@link NumFactory#numOf(Number)}, whose
     * coarse factory precision can round an in-range weight such as {@code 0.99} to
     * its boundary), so the combined sum uses the same weights the per-product Num
     * arithmetic applied.
     *
     * @param factory      the factory that narrows the exact sum
     * @param first        the exact expansion of the first operand
     * @param firstWeight  the first convex weight
     * @param second       the exact expansion of the second operand
     * @param secondWeight the second convex weight
     * @return the exactly combined, once-narrowed weighted sum
     */
    static Num exactWeightedSum(NumFactory factory, BigDecimal first, double firstWeight, BigDecimal second,
            double secondWeight) {
        return narrowWeightedSum(factory, first, new BigDecimal(firstWeight), second, new BigDecimal(secondWeight));
    }

    /**
     * The convex combination {@code first * firstWeight + second *
     * secondWeight}, combined without intermediate rounding and narrowed once
     * through {@code factory}. {@link DoubleNum} weights retain their exact binary
     * value; other {@link Num} implementations retain their exact decimal expansion
     * instead of an underflowing primitive-double projection.
     *
     * @param factory      the factory that narrows the exact sum
     * @param first        the exact expansion of the first operand
     * @param firstWeight  the first convex weight
     * @param second       the exact expansion of the second operand
     * @param secondWeight the second convex weight
     * @return the exactly combined, once-narrowed weighted sum
     */
    static Num exactWeightedSum(NumFactory factory, BigDecimal first, Num firstWeight, BigDecimal second,
            Num secondWeight) {
        return narrowWeightedSum(factory, first, exactValueOf(firstWeight), second, exactValueOf(secondWeight));
    }

    private static Num narrowWeightedSum(NumFactory factory, BigDecimal first, BigDecimal firstWeight,
            BigDecimal second, BigDecimal secondWeight) {
        BigDecimal sum = first.multiply(firstWeight).add(second.multiply(secondWeight));
        return factory.numOf(sum);
    }
}
