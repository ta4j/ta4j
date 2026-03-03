/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.walkforward;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.walkforward.PredictionProvider;
import org.ta4j.core.walkforward.RankedPrediction;

/**
 * Elliott-specific prediction provider for generic walk-forward engines.
 *
 * <p>
 * This adapter runs
 * {@link org.ta4j.core.indicators.elliott.ElliottWaveAnalysisRunner} on a
 * prefix-selected series and maps ranked base scenarios to
 * {@link RankedPrediction} rows.
 *
 * @since 0.22.4
 */
public final class ElliottWavePredictionProvider
        implements PredictionProvider<ElliottWaveWalkForwardContext, ElliottWaveAnalysisResult.BaseScenarioAssessment> {

    @Override
    public List<RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment>> predict(BarSeries fullSeries,
            int decisionIndex, ElliottWaveWalkForwardContext context) {
        Objects.requireNonNull(fullSeries, "fullSeries");
        Objects.requireNonNull(context, "context");

        BarSeries selected = context.selectSeries(fullSeries, decisionIndex);
        if (selected == null || selected.isEmpty()) {
            return List.of();
        }

        ElliottWaveAnalysisResult result = context.runner().analyze(selected);
        List<ElliottWaveAnalysisResult.BaseScenarioAssessment> scenarios = result.rankedBaseScenarios();
        if (scenarios.isEmpty()) {
            return List.of();
        }

        int max = Math.min(context.maxPredictions(), scenarios.size());
        List<RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment>> predictions = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            ElliottWaveAnalysisResult.BaseScenarioAssessment assessment = scenarios.get(i);
            String id = assessment.scenario().id();
            double probability = clamp01(assessment.compositeScore());
            double confidence = clamp01(assessment.confidenceScore());
            predictions.add(new RankedPrediction<>(id, i + 1, probability, confidence, assessment));
        }
        return List.copyOf(predictions);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
