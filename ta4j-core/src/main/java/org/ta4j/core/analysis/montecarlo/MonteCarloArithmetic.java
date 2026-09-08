/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Package-private arithmetic shared by Monte Carlo decorators.
 *
 * <p>
 * The affine operation is deliberately kept in the active {@link Num} domain.
 * Double-backed values use a fused multiply-add when the endpoint difference is
 * safe, while opposite-sign endpoints use weighted endpoints so their
 * difference is never formed first. Decimal-backed values therefore never pass
 * through primitive {@code double} arithmetic.
 * </p>
 */
final class MonteCarloArithmetic {

    private MonteCarloArithmetic() {
    }

    /**
     * Evaluates {@code center + scale * (sample - center)} without avoidable
     * intermediate overflow.
     *
     * @return the finite result, or {@code null} when the mathematical result
     *         cannot be represented by the active numeric domain
     */
    static Num affine(Num center, Num sample, Num scale, NumFactory numFactory) {
        Num result;
        boolean oppositeSigns = center.isNegative() != sample.isNegative();
        if (oppositeSigns) {
            // Both coefficients are non-negative for contraction and have the
            // same sign for expansion. In either case, this avoids an endpoint
            // difference that may exceed the DoubleNum range.
            result = center.multipliedBy(numFactory.one().minus(scale)).plus(sample.multipliedBy(scale));
        } else if (center instanceof DoubleNum && sample instanceof DoubleNum && scale instanceof DoubleNum) {
            double centerValue = center.doubleValue();
            double sampleValue = sample.doubleValue();
            double scaleValue = scale.doubleValue();
            // Same-sign finite endpoints have a representable difference. FMA
            // also keeps a large product from overflowing before cancellation.
            double value = Math.fma(scaleValue, sampleValue - centerValue, centerValue);
            return Double.isFinite(value) ? numFactory.numOf(value) : null;
        } else {
            result = center.plus(sample.minus(center).multipliedBy(scale));
        }
        return Num.isFinite(result) ? result : null;
    }
}
