/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.optimization.ga;

import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

/**
 * Builds an indicator phenotype from decoded candidate parameters.
 *
 * @param <T> indicator value type
 * @since 0.22.7
 */
@FunctionalInterface
public interface IndicatorCandidateFactory<T> {

    /**
     * Creates an indicator for one decoded parameter set.
     *
     * @param series           bar series associated with the indicator
     * @param sourceIndicators optional source indicators used by the candidate
     *                         family
     * @param parameters       decoded candidate parameters
     * @return constructed indicator
     * @since 0.22.7
     */
    Indicator<T> create(BarSeries series, List<? extends Indicator<?>> sourceIndicators,
            CandidateCodec.ParameterValues parameters);
}
