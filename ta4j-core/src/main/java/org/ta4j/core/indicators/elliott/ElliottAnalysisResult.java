/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.ta4j.core.num.NaN.NaN;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.indicators.elliott.confidence.ElliottConfidenceBreakdown;

/**
 * Result of an Elliott Wave analysis run.
 *
 * @param degree               Elliott degree used for analysis
 * @param index                bar index evaluated
 * @param rawSwings            raw detected swings
 * @param processedSwings      swings after filtering/compression
 * @param scenarios            scenario set generated from processed swings
 * @param confidenceBreakdowns confidence breakdowns keyed by scenario id
 * @param channel              projected Elliott channel
 * @param trendBias            aggregate scenario trend bias
 * @since 0.22.2
 */
public record ElliottAnalysisResult(ElliottDegree degree, int index, List<ElliottSwing> rawSwings,
        List<ElliottSwing> processedSwings, ElliottScenarioSet scenarios,
        Map<String, ElliottConfidenceBreakdown> confidenceBreakdowns, ElliottChannel channel,
        ElliottTrendBias trendBias) {

    public ElliottAnalysisResult {
        Objects.requireNonNull(degree, "degree");
        rawSwings = rawSwings == null ? List.of() : List.copyOf(rawSwings);
        processedSwings = processedSwings == null ? List.of() : List.copyOf(processedSwings);
        scenarios = scenarios == null ? ElliottScenarioSet.empty(index) : scenarios;
        confidenceBreakdowns = confidenceBreakdowns == null ? Map.of() : Map.copyOf(confidenceBreakdowns);
        channel = channel == null ? new ElliottChannel(NaN, NaN, NaN) : channel;
        trendBias = trendBias == null ? ElliottTrendBias.unknown() : trendBias;
    }

    /**
     * Returns the confidence breakdown for a specific scenario.
     *
     * @param scenario scenario to lookup
     * @return confidence breakdown, if available
     * @since 0.22.2
     */
    public Optional<ElliottConfidenceBreakdown> breakdownFor(final ElliottScenario scenario) {
        if (scenario == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(confidenceBreakdowns.get(scenario.id()));
    }
}
