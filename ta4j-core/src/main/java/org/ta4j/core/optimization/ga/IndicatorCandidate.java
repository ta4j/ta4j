/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.optimization.ga;

import java.util.Objects;

import org.ta4j.core.Indicator;

/**
 * Decoded indicator phenotype produced from a candidate chromosome.
 *
 * @param displayName readable candidate name including the searched family and
 *                    parameter values
 * @param indicator   constructed indicator instance
 * @param parameters  decoded parameter values used to construct the indicator
 * @param <T>         indicator value type
 * @since 0.22.7
 */
public record IndicatorCandidate<T>(String displayName, Indicator<T> indicator,
        CandidateCodec.ParameterValues parameters) {

    /**
     * Creates a validated indicator candidate.
     *
     * @since 0.22.7
     */
    public IndicatorCandidate {
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        Objects.requireNonNull(indicator, "indicator");
        Objects.requireNonNull(parameters, "parameters");
    }
}
