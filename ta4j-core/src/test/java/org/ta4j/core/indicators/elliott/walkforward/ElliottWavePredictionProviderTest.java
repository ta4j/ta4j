/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.walkforward;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.walkforward.RankedPrediction;
import org.ta4j.core.walkforward.WalkForwardConfig;
import org.ta4j.core.indicators.elliott.ElliottWaveAnalysisResult;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

class ElliottWavePredictionProviderTest {

    @Test
    void baselineProfileBuildsConfiguredContext() {
        ElliottWaveWalkForwardContext context = ElliottWaveWalkForwardProfiles.baseline();

        assertThat(context.runner()).isNotNull();
        assertThat(context.maxPredictions()).isEqualTo(5);
        assertThat(context.metadata()).containsEntry("profile", "baseline-minute-f2-h2l2-max25-sw0")
                .containsEntry("degree", ElliottWaveWalkForwardProfiles.BASELINE_DEGREE.name());
        assertThat(ElliottWaveWalkForwardProfiles.baselineConfig()).isNotEqualTo(WalkForwardConfig.defaultConfig());
        assertThat(ElliottWaveWalkForwardProfiles.baselineConfig())
                .isEqualTo(new WalkForwardConfig(252, 200, 65, 5, 5, 320, 60, List.of(30, 150), 3, List.of(1, 5), 42L));
    }

    @Test
    void providerReturnsRankedPredictionsBoundedByContextLimit() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(360)).build();
        ElliottWaveWalkForwardContext context = ElliottWaveWalkForwardProfiles.baseline();
        ElliottWavePredictionProvider provider = new ElliottWavePredictionProvider();

        List<RankedPrediction<ElliottWaveAnalysisResult.BaseScenarioAssessment>> predictions = provider.predict(series,
                series.getEndIndex(), context);

        Num zero = series.numFactory().zero();
        Num one = series.numFactory().one();
        assertThat(predictions.size()).isLessThanOrEqualTo(context.maxPredictions());
        for (int i = 0; i < predictions.size(); i++) {
            assertThat(predictions.get(i).rank()).isEqualTo(i + 1);
            assertThat(predictions.get(i).probability()).isGreaterThanOrEqualTo(zero).isLessThanOrEqualTo(one);
            assertThat(predictions.get(i).confidence()).isGreaterThanOrEqualTo(zero).isLessThanOrEqualTo(one);
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
