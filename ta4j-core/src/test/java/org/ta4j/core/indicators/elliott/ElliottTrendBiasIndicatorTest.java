/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

class ElliottTrendBiasIndicatorTest {

    private final NumFactory numFactory = DecimalNumFactory.getInstance();

    @Test
    void computesBiasFromScenarioIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 101, 102).build();
        ElliottScenario bullish = scenario("bull", 0.8, true);
        ElliottScenario bearish = scenario("bear", 0.4, false);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(bullish, bearish), series.getEndIndex());
        ElliottScenarioIndicator scenarioIndicator = new StubScenarioIndicator(series, set);

        ElliottTrendBiasIndicator indicator = new ElliottTrendBiasIndicator(scenarioIndicator, 0.1);

        ElliottTrendBias bias = indicator.getValue(series.getEndIndex());

        assertThat(bias.isBullish()).isTrue();
        assertThat(bias.score()).isGreaterThan(0.0);
        assertThat(bias.consensus()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void serializationRoundTrip() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
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
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottTrendBiasIndicator indicator = new ElliottTrendBiasIndicator(scenarioIndicator, 0.2);

        String json = indicator.toJson();
        Indicator<ElliottTrendBias> restored = (Indicator<ElliottTrendBias>) Indicator.fromJson(series, json);

        assertThat(indicator.toDescriptor()).isEqualTo(restored.toDescriptor());

        ElliottTrendBias originalValue = indicator.getValue(series.getEndIndex());
        ElliottTrendBias restoredValue = restored.getValue(series.getEndIndex());
        assertThat(restoredValue.direction()).isEqualTo(originalValue.direction());
        assertThat(restoredValue.score()).isCloseTo(originalValue.score(), within(1.0e-6));
        assertThat(restoredValue.knownDirectionCount()).isEqualTo(originalValue.knownDirectionCount());
    }

    private ElliottScenario scenario(String id, double confidence, boolean bullish) {
        return ElliottScenario.builder()
                .id(id)
                .currentPhase(ElliottPhase.WAVE3)
                .confidence(new ElliottConfidence(numFactory.numOf(confidence), numFactory.numOf(confidence),
                        numFactory.numOf(confidence), numFactory.numOf(confidence), numFactory.numOf(confidence),
                        numFactory.numOf(confidence), "Test"))
                .type(ScenarioType.IMPULSE)
                .bullishDirection(bullish)
                .build();
    }

    private static final class StubScenarioIndicator extends ElliottScenarioIndicator {
        private final ElliottScenarioSet set;

        private StubScenarioIndicator(final BarSeries series, final ElliottScenarioSet set) {
            super(new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR));
            this.set = set;
        }

        @Override
        protected ElliottScenarioSet calculate(final int index) {
            return set;
        }
    }
}
