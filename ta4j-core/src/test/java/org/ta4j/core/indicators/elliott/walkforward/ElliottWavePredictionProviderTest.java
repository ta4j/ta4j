/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.walkforward;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.walkforward.RankedPrediction;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class ElliottWavePredictionProviderTest {

    @Test
    void baselineProfileBuildsConfiguredContext() {
        ElliottWaveWalkForwardContext context = ElliottWaveWalkForwardProfiles.baseline(ElliottDegree.MINOR);

        assertThat(context.runner()).isNotNull();
        assertThat(context.maxPredictions()).isEqualTo(5);
        assertThat(context.metadata()).containsEntry("profile", "baseline");
    }

    @Test
    void providerReturnsRankedPredictionsBoundedByContextLimit() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(360)).build();
        ElliottWaveWalkForwardContext context = ElliottWaveWalkForwardProfiles.baseline(ElliottDegree.MINOR);
        ElliottWavePredictionProvider provider = new ElliottWavePredictionProvider();

        List<RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment>> predictions = provider.predict(series,
                series.getEndIndex(), context);

        assertThat(predictions.size()).isLessThanOrEqualTo(context.maxPredictions());
        for (int i = 0; i < predictions.size(); i++) {
            assertThat(predictions.get(i).rank()).isEqualTo(i + 1);
            assertThat(predictions.get(i).probability()).isBetween(0.0, 1.0);
            assertThat(predictions.get(i).confidence()).isBetween(0.0, 1.0);
        }
    }

    private static double[] prices(int size) {
        double[] prices = new double[size];
        for (int i = 0; i < size; i++) {
            prices[i] = 100 + Math.sin(i / 8.0) * 6 + i * 0.04;
        }
        return prices;
    }
}
