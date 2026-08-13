/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import java.util.Objects;

import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Validated common moments shared by return-state estimators.
 *
 * <p>
 * Variance is the canonical dispersion value; {@link #volatility()} is always
 * derived from it. An unstable state may retain a positive observation count,
 * but its moment values use {@link NaN#NaN}.
 *
 * @param index            source index
 * @param observationCount observations incorporated by the estimator
 * @param isStable         whether the moments are usable
 * @param representation   return representation
 * @param mean             mean return
 * @param drift            forward drift assumption
 * @param variance         return variance
 * @since 0.23.1
 */
public record ReturnMoments(int index, int observationCount, boolean isStable, ReturnRepresentation representation,
        Num mean, Num drift, Num variance) {

    /**
     * Creates validated return moments.
     *
     * @since 0.23.1
     */
    public ReturnMoments {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        if (observationCount < 0) {
            throw new IllegalArgumentException("observationCount must be >= 0");
        }
        if (isStable && observationCount == 0) {
            throw new IllegalArgumentException("stable moments must include at least one observation");
        }
        representation = Objects.requireNonNull(representation, "representation must not be null");
        mean = Objects.requireNonNull(mean, "mean must not be null");
        drift = Objects.requireNonNull(drift, "drift must not be null");
        variance = Objects.requireNonNull(variance, "variance must not be null");
        if (isStable) {
            requireFinite(mean, "mean");
            requireFinite(drift, "drift");
            requireFinite(variance, "variance");
            if (variance.isNegative()) {
                throw new IllegalArgumentException("variance must be >= 0");
            }
        } else if (!mean.isNaN() || !drift.isNaN() || !variance.isNaN()) {
            throw new IllegalArgumentException("unstable moments must use NaN values");
        }
    }

    /**
     * Creates stable moments.
     *
     * @param index            source index
     * @param observationCount observations incorporated by the estimator
     * @param representation   return representation
     * @param mean             finite mean return
     * @param drift            finite forward drift assumption
     * @param variance         finite, non-negative return variance
     * @return stable return moments
     * @since 0.23.1
     */
    public static ReturnMoments stable(int index, int observationCount, ReturnRepresentation representation, Num mean,
            Num drift, Num variance) {
        return new ReturnMoments(index, observationCount, true, representation, mean, drift, variance);
    }

    /**
     * Creates unavailable moments while retaining known observation provenance.
     *
     * @param index            source index
     * @param observationCount observations incorporated by the estimator
     * @param representation   return representation
     * @return unstable return moments
     * @since 0.23.1
     */
    public static ReturnMoments unstable(int index, int observationCount, ReturnRepresentation representation) {
        return new ReturnMoments(index, observationCount, false, representation, NaN.NaN, NaN.NaN, NaN.NaN);
    }

    /**
     * Returns volatility derived from canonical variance.
     *
     * @return volatility, or {@code NaN.NaN} when unstable
     * @since 0.23.1
     */
    public Num volatility() {
        if (!isStable) {
            return NaN.NaN;
        }
        return variance.isZero() ? variance.getNumFactory().zero() : variance.sqrt();
    }

    private static void requireFinite(Num value, String fieldName) {
        if (!Num.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite for stable moments");
        }
    }
}
