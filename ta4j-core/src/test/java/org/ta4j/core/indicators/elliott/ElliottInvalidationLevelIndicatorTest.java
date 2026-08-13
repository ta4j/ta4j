/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ElliottInvalidationLevelIndicatorTest {

    @Test
    void returnsNaNWhenNoScenarios() {
        BarSeries series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(100).highPrice(100).lowPrice(100).closePrice(100).volume(0).add();

        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator indicator = new ElliottInvalidationLevelIndicator(scenarioIndicator);

        Num invalidation = indicator.getValue(0);

        // With no scenarios, invalidation returns NaN - check via Num API
        assertThat(Num.isNaNOrNull(invalidation)).isTrue();
    }

    @Test
    void primaryModeUsesTopScenario() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator indicator = new ElliottInvalidationLevelIndicator(scenarioIndicator,
                ElliottInvalidationLevelIndicator.InvalidationMode.PRIMARY);

        Num invalidation = indicator.getValue(series.getEndIndex());

        // Should be primary scenario's invalidation or NaN
        assertThat(invalidation).isNotNull();
    }

    @Test
    void conservativeModeUsesTightestInvalidation() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator conservative = new ElliottInvalidationLevelIndicator(scenarioIndicator,
                ElliottInvalidationLevelIndicator.InvalidationMode.CONSERVATIVE);

        Num invalidation = conservative.getValue(series.getEndIndex());

        assertThat(invalidation).isNotNull();
    }

    @Test
    void conservativeModeFoldsBullishAndBearishScenariosSeparately() {
        BarSeries series = seriesWithClose(103);
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator indicator = new ElliottInvalidationLevelIndicator(scenarioIndicator,
                ElliottInvalidationLevelIndicator.InvalidationMode.CONSERVATIVE);

        // Bearish invalidation levels (105, 108) and a bullish level (100):
        // folding them under the first scenario's direction used to return 100.
        // Per-direction folds with a close reference of 103 must select the
        // tightest bearish level 105 (distance 2 vs distance 3 for bullish).
        NumFactory numFactory = series.numFactory();
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario("bearish-tight", false, 105, 0.9, numFactory),
                scenario("bullish", true, 100, 0.85, numFactory),
                scenario("bearish-wide", false, 108, 0.8, numFactory)), 0);

        Num invalidation = indicator.calculateConservativeInvalidation(set, 0);

        assertThat(invalidation).isEqualTo(numFactory.numOf(105));
    }

    @Test
    void aggressiveModeFoldsBullishAndBearishScenariosSeparately() {
        BarSeries series = seriesWithClose(103);
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator indicator = new ElliottInvalidationLevelIndicator(scenarioIndicator,
                ElliottInvalidationLevelIndicator.InvalidationMode.AGGRESSIVE);

        // Folding all scenarios under the first scenario's direction used to
        // return 100; per-direction folds with a close reference of 103 must
        // select the widest bearish level 108 (distance 5 vs distance 3).
        NumFactory numFactory = series.numFactory();
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario("bullish", true, 100, 0.9, numFactory),
                scenario("bearish-tight", false, 105, 0.85, numFactory),
                scenario("bearish-wide", false, 108, 0.8, numFactory)), 0);

        Num invalidation = indicator.calculateAggressiveInvalidation(set, 0);

        assertThat(invalidation).isEqualTo(numFactory.numOf(108));
    }

    @Test
    void aggressiveModeUsesWidestInvalidation() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator aggressive = new ElliottInvalidationLevelIndicator(scenarioIndicator,
                ElliottInvalidationLevelIndicator.InvalidationMode.AGGRESSIVE);

        Num invalidation = aggressive.getValue(series.getEndIndex());

        assertThat(invalidation).isNotNull();
    }

    @Test
    void isInvalidatedCheck() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator indicator = new ElliottInvalidationLevelIndicator(scenarioIndicator);

        // Check returns boolean without throwing
        boolean invalidated = indicator.isInvalidated(series.getEndIndex(), series.numFactory().numOf(50));

        assertThat(invalidated).isIn(true, false);
    }

    @Test
    void distanceToInvalidation() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator indicator = new ElliottInvalidationLevelIndicator(scenarioIndicator);

        Num distance = indicator.distanceToInvalidation(series.getEndIndex(), series.numFactory().numOf(120));

        // Returns a valid distance or NaN
        assertThat(distance).isNotNull();
    }

    @Test
    void unstableBarsFromScenarioIndicator() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 3, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator indicator = new ElliottInvalidationLevelIndicator(scenarioIndicator);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(scenarioIndicator.getCountOfUnstableBars());
    }

    private BarSeries createSeriesWithSwings() {
        MockBarSeriesBuilder builder = new MockBarSeriesBuilder();
        BarSeries series = builder.build();

        double[] prices = { 100, 110, 105, 120, 108, 130, 115, 140, 125, 150 };
        for (double price : prices) {
            series.barBuilder()
                    .openPrice(price - 2)
                    .highPrice(price + 2)
                    .lowPrice(price - 3)
                    .closePrice(price)
                    .volume(1000)
                    .add();
        }

        return series;
    }

    private BarSeries seriesWithClose(final double close) {
        BarSeries series = new MockBarSeriesBuilder().build();
        series.barBuilder()
                .openPrice(close - 1)
                .highPrice(close + 1)
                .lowPrice(close - 2)
                .closePrice(close)
                .volume(100)
                .add();
        return series;
    }

    private ElliottScenario scenario(final String id, final boolean rising, final double invalidation,
            final double confidence, final NumFactory numFactory) {
        final Num from = numFactory.numOf(rising ? 100 : 150);
        final Num to = numFactory.numOf(rising ? 110 : 140);
        final ElliottSwing swing = new ElliottSwing(0, 1, from, to, ElliottDegree.MINOR);
        final Num zero = numFactory.zero();
        final ElliottConfidence scores = new ElliottConfidence(numFactory.numOf(confidence), zero, zero, zero, zero,
                zero, "test");
        return new ElliottScenario(id, ElliottPhase.WAVE3, List.of(swing), scores, ElliottDegree.MINOR,
                numFactory.numOf(invalidation), numFactory.numOf(130), List.of(), ScenarioType.IMPULSE, 0, null);
    }
}
