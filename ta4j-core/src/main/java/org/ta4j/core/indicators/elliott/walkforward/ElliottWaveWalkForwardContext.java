/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.walkforward;

import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.SeriesSelector;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner;

/**
 * Context used by Elliott walk-forward prediction provider.
 *
 * @param runner         configured Elliott analysis runner
 * @param seriesSelector selector used to provide the analysis series for each
 *                       decision index
 * @param maxPredictions max ranked predictions to keep per decision index
 * @param metadata       optional run metadata
 * @since 0.22.4
 */
public record ElliottWaveWalkForwardContext(ElliottWaveAnalysisRunner runner, SeriesSelector<Integer> seriesSelector,
        int maxPredictions, Map<String, String> metadata) {

    /**
     * Creates a validated context.
     */
    public ElliottWaveWalkForwardContext {
        Objects.requireNonNull(runner, "runner");
        seriesSelector = seriesSelector == null ? defaultPrefixSelector() : seriesSelector;
        if (maxPredictions <= 0) {
            throw new IllegalArgumentException("maxPredictions must be > 0");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Creates context with the default prefix selector.
     *
     * @param runner         configured Elliott analysis runner
     * @param maxPredictions max predictions to retain
     * @return context
     * @since 0.22.4
     */
    public static ElliottWaveWalkForwardContext of(ElliottWaveAnalysisRunner runner, int maxPredictions) {
        return new ElliottWaveWalkForwardContext(runner, defaultPrefixSelector(), maxPredictions, Map.of());
    }

    private static SeriesSelector<Integer> defaultPrefixSelector() {
        return (series, decisionIndex) -> {
            Objects.requireNonNull(series, "series");
            if (decisionIndex < series.getBeginIndex() || decisionIndex > series.getEndIndex()) {
                throw new IllegalArgumentException("decisionIndex out of range");
            }
            return series.getSubSeries(series.getBeginIndex(), decisionIndex + 1);
        };
    }

    /**
     * Selects the series for a decision index.
     *
     * @param series        root series
     * @param decisionIndex decision index
     * @return selected series
     * @since 0.22.4
     */
    public BarSeries selectSeries(BarSeries series, int decisionIndex) {
        return seriesSelector.select(series, decisionIndex);
    }
}
