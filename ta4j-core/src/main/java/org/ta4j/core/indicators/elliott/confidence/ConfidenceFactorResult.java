/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Result of scoring a confidence factor.
 *
 * @param name        factor name
 * @param category    factor category
 * @param score       factor score (0.0 - 1.0)
 * @param weight      applied weight
 * @param diagnostics diagnostic key/value pairs
 * @param summary     short summary text
 * @since 0.22.2
 */
public record ConfidenceFactorResult(String name, ConfidenceFactorCategory category, Num score, double weight,
        Map<String, Number> diagnostics, String summary) {

    public ConfidenceFactorResult {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(score, "score");
        diagnostics = diagnostics == null ? Map.of() : Collections.unmodifiableMap(diagnostics);
    }

    /**
     * Creates an unweighted result.
     *
     * @param name        factor name
     * @param category    factor category
     * @param score       factor score
     * @param diagnostics diagnostics map
     * @param summary     summary text
     * @return result with zero weight
     * @since 0.22.2
     */
    public static ConfidenceFactorResult of(final String name, final ConfidenceFactorCategory category, final Num score,
            final Map<String, Number> diagnostics, final String summary) {
        return new ConfidenceFactorResult(name, category, score, 0.0, diagnostics, summary);
    }

    /**
     * Returns a copy with the given weight applied.
     *
     * @param weight weight to apply
     * @return weighted result
     * @since 0.22.2
     */
    public ConfidenceFactorResult withWeight(final double weight) {
        return new ConfidenceFactorResult(name, category, score, weight, diagnostics, summary);
    }
}
