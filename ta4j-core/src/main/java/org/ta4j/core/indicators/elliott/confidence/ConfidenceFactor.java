/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

/**
 * Confidence factor for Elliott wave scoring.
 *
 * <p>
 * Implement this interface to introduce a new scoring dimension (for example,
 * custom volatility or volume-based checks). Factors are assembled into
 * {@link ConfidenceProfile} instances and weighted by {@link ConfidenceModel}
 * implementations.
 *
 * @since 0.22.2
 */
public interface ConfidenceFactor {

    /**
     * @return human-readable factor name
     * @since 0.22.2
     */
    String name();

    /**
     * @return factor category
     * @since 0.22.2
     */
    ConfidenceFactorCategory category();

    /**
     * Scores the factor for the given context.
     *
     * @param context confidence scoring context
     * @return factor result (without weight applied)
     * @since 0.22.2
     */
    ConfidenceFactorResult score(ElliottConfidenceContext context);
}
