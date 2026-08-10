/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * One lag entry of a {@link LagCorrelationProfile}.
 *
 * <p>
 * A point is <em>defined</em> when its window had enough history and its
 * correlation is a finite number. Undefined points are retained in the profile
 * so callers can see which lags lacked history or variance; they never
 * participate in best-lag selection. A window with enough history but no
 * variance reports {@code sampleCount = barCount} with a {@code NaN}
 * correlation, while a window that is entirely unavailable reports
 * {@code sampleCount = 0}.
 * </p>
 *
 * @param lag         the lag, following the {@link LaggedCorrelationIndicator}
 *                    sign convention (positive means the first indicator leads
 *                    the second)
 * @param correlation the Pearson correlation at this lag, or {@code NaN} when
 *                    the lag is undefined
 * @param sampleCount aligned samples actually available in the window, or
 *                    {@code 0} when the window itself was unavailable
 * @since 0.24.1
 */
public record LagCorrelationPoint(int lag, Num correlation, int sampleCount) {

    /**
     * Validates the point.
     *
     * @throws NullPointerException     if {@code correlation} is null
     * @throws IllegalArgumentException if {@code sampleCount} is negative
     */
    public LagCorrelationPoint {
        Objects.requireNonNull(correlation, "correlation");
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be >= 0");
        }
        if (!correlation.isNaN() && !CorrelationWindowSupport.isFinite(correlation)) {
            throw new IllegalArgumentException("correlation must be finite or NaN");
        }
    }

    /**
     * @return {@code true} when the correlation is a defined (finite) number
     */
    public boolean isDefined() {
        return !correlation.isNaN();
    }
}
