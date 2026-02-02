/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import java.util.List;

import org.ta4j.core.indicators.elliott.ElliottConfidence;

/**
 * Confidence score along with factor-level breakdown.
 *
 * @param confidence aggregated confidence metrics
 * @param factors    weighted factor results
 * @since 0.22.2
 */
public record ElliottConfidenceBreakdown(ElliottConfidence confidence, List<ConfidenceFactorResult> factors) {

    public ElliottConfidenceBreakdown {
        factors = factors == null ? List.of() : List.copyOf(factors);
    }
}
