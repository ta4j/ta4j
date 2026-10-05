/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.elliott.EmpiricalElliottWaveForecastIndicator.Settings;
import org.ta4j.core.indicators.elliott.EmpiricalElliottWaveForecastIndicator.WaveForecast;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

class EmpiricalElliottWaveForecastIndicatorTest {

    @Test
    void forecastsOnlyFromEarlierEmpiricalStructures() {
        BarSeries series = patternedSeries(100);
        AtomicInteger highestRequestedIndex = new AtomicInteger(-1);
        FixedScenarioIndicator scenarios = new FixedScenarioIndicator(series, ElliottPhase.WAVE2,
                highestRequestedIndex);
        Settings settings = new Settings(ElliottDegree.SUB_MINUETTE, 80, 16, 8, 100.0d, 0.0d);
        EmpiricalElliottWaveForecastIndicator indicator = new EmpiricalElliottWaveForecastIndicator(series, scenarios,
                settings);

        WaveForecast forecast = indicator.getValue(series.getEndIndex());

        assertThat(forecast.isStable()).isTrue();
        assertThat(forecast.mostLikelyPhase()).isEqualTo(ElliottPhase.WAVE2);
        assertThat(forecast.probability()).isEqualByComparingTo(series.numFactory().one());
        assertThat(forecast.waveNumberForecast().sampleCount()).isEqualTo(16);
        assertThat(highestRequestedIndex).hasValueLessThan(series.getEndIndex());
    }

    @Test
    void refusesToForecastWhenNoHistoricalWaveStructureExists() {
        BarSeries series = patternedSeries(100);
        FixedScenarioIndicator scenarios = new FixedScenarioIndicator(series, ElliottPhase.NONE, new AtomicInteger());
        Settings settings = new Settings(ElliottDegree.SUB_MINUETTE, 80, 16, 8, 100.0d, 0.0d);
        EmpiricalElliottWaveForecastIndicator indicator = new EmpiricalElliottWaveForecastIndicator(series, scenarios,
                settings);

        WaveForecast forecast = indicator.getValue(series.getEndIndex());

        assertThat(forecast.isStable()).isFalse();
        assertThat(forecast.mostLikelyPhase()).isEqualTo(ElliottPhase.NONE);
        assertThat(forecast.phaseProbabilities()).isEmpty();
        assertThat(forecast.waveNumberForecast().sampleCount()).isZero();
    }

    @Test
    void exposesAllImpulsePhaseProbabilities() {
        BarSeries series = patternedSeries(100);
        FixedScenarioIndicator scenarios = new FixedScenarioIndicator(series, null, new AtomicInteger());
        Settings settings = new Settings(ElliottDegree.SUB_MINUETTE, 80, 20, 10, 100.0d, 0.0d);
        EmpiricalElliottWaveForecastIndicator indicator = new EmpiricalElliottWaveForecastIndicator(series, scenarios,
                settings);

        WaveForecast forecast = indicator.getValue(series.getEndIndex());
        Num total = forecast.phaseProbabilities().values().stream().reduce(series.numFactory().zero(), Num::plus);

        assertThat(forecast.isStable()).isTrue();
        assertThat(forecast.phaseProbabilities()).containsOnlyKeys(ElliottPhase.WAVE1, ElliottPhase.WAVE2,
                ElliottPhase.WAVE3, ElliottPhase.WAVE4, ElliottPhase.WAVE5);
        assertThat(total).isEqualByComparingTo(series.numFactory().one());
    }

    @Test
    void labelsAnalogsAtTheForecastHorizon() {
        BarSeries series = flatSeries(100);
        FixedScenarioIndicator scenarios = new FixedScenarioIndicator(series, null, new AtomicInteger());
        Settings settings = new Settings(ElliottDegree.SUB_MINUETTE, 20, 1, 1, 100.0d, 0.0d);
        EmpiricalElliottWaveForecastIndicator indicator = new EmpiricalElliottWaveForecastIndicator(series, scenarios,
                settings);

        WaveForecast forecast = indicator.getValue(series.getEndIndex());

        assertThat(forecast.isStable()).isTrue();
        assertThat(forecast.waveNumberForecast().horizon()).isEqualTo(1);
        assertThat(forecast.mostLikelyPhase()).isEqualTo(ElliottPhase.WAVE1);
    }

    @Test
    void reportsWarmupFromFeaturesScenariosAndMinimumSamples() {
        BarSeries series = patternedSeries(100);
        FixedScenarioIndicator scenarios = new FixedScenarioIndicator(series, ElliottPhase.WAVE1, new AtomicInteger());
        Settings settings = new Settings(ElliottDegree.SUB_MINUETTE, 80, 16, 8, 100.0d, 0.0d);
        EmpiricalElliottWaveForecastIndicator indicator = new EmpiricalElliottWaveForecastIndicator(series, scenarios,
                settings);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(42);
        assertThat(indicator.getValue(41).isStable()).isFalse();
    }

    @Test
    void validatesEmpiricalSettings() {
        assertThatThrownBy(() -> new Settings(ElliottDegree.SUB_MINUETTE, 100, 4, 5, 2.0d, 0.2d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimumSamples");
        assertThatThrownBy(() -> new Settings(ElliottDegree.SUB_MINUETTE, 4, 5, 5, 2.0d, 0.2d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trainingLookbackBars");
        assertThatThrownBy(() -> new Settings(ElliottDegree.SUB_MINUETTE, 100, 5, 2, Double.NaN, 0.2d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximumAnalogDistance");
        assertThat(new Settings(ElliottDegree.SUB_MINUETTE, 5, 5, 5, 2.0d, 0.2d).trainingLookbackBars()).isEqualTo(5);
    }

    @Test
    void rejectsContradictoryPublicWaveForecastState() {
        BarSeries series = patternedSeries(10);
        Forecast summary = Forecast.ofSamples(9, 1, List.of(series.numFactory().one(), series.numFactory().two(),
                series.numFactory().two(), series.numFactory().two()));
        Map<ElliottPhase, Num> probabilities = Map.of(ElliottPhase.WAVE1, series.numFactory().numOf(0.25d),
                ElliottPhase.WAVE2, series.numFactory().numOf(0.75d));

        assertThatThrownBy(() -> new WaveForecast(summary, probabilities, ElliottPhase.WAVE1,
                probabilities.get(ElliottPhase.WAVE1))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mostLikelyPhase");
        assertThatThrownBy(
                () -> new WaveForecast(summary, probabilities, ElliottPhase.WAVE2, series.numFactory().numOf(0.80d)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probability");
        Map<ElliottPhase, Num> excessiveMass = Map.of(ElliottPhase.WAVE1, series.numFactory().numOf(0.75d),
                ElliottPhase.WAVE2, series.numFactory().numOf(0.75d));
        assertThatThrownBy(() -> new WaveForecast(summary, excessiveMass, ElliottPhase.WAVE1,
                excessiveMass.get(ElliottPhase.WAVE1))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to 1");
        Forecast mismatchedSummary = Forecast.ofSamples(9, 1, List.of(series.numFactory().one()));
        assertThatThrownBy(() -> new WaveForecast(mismatchedSummary, probabilities, ElliottPhase.WAVE2,
                probabilities.get(ElliottPhase.WAVE2))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numeric forecast mean");

        WaveForecast valid = new WaveForecast(summary, probabilities, ElliottPhase.WAVE2,
                probabilities.get(ElliottPhase.WAVE2));
        assertThat(valid.mostLikelyPhase()).isEqualTo(ElliottPhase.WAVE2);
        assertThat(valid.probability()).isEqualByComparingTo(probabilities.get(ElliottPhase.WAVE2));
    }

    private static BarSeries patternedSeries(final int count) {
        BarSeries series = new MockBarSeriesBuilder().build();
        for (int i = 0; i < count; i++) {
            double close = 100.0d + i * 0.1d + Math.sin(i * 0.5d);
            series.barBuilder()
                    .openPrice(close - 0.1d)
                    .highPrice(close + 0.4d)
                    .lowPrice(close - 0.4d)
                    .closePrice(close)
                    .volume(100.0d + i % 7)
                    .add();
        }
        return series;
    }

    private static BarSeries flatSeries(final int count) {
        BarSeries series = new MockBarSeriesBuilder().build();
        for (int index = 0; index < count; index++) {
            series.barBuilder()
                    .openPrice(100.0d)
                    .highPrice(100.5d)
                    .lowPrice(99.5d)
                    .closePrice(100.0d)
                    .volume(100.0d)
                    .add();
        }
        return series;
    }

    private static final class FixedScenarioIndicator extends AbstractIndicator<ElliottScenarioSet> {

        private final ElliottPhase fixedPhase;
        private final AtomicInteger highestRequestedIndex;

        private FixedScenarioIndicator(final BarSeries series, final ElliottPhase fixedPhase,
                final AtomicInteger highestRequestedIndex) {
            super(series);
            this.fixedPhase = fixedPhase;
            this.highestRequestedIndex = highestRequestedIndex;
        }

        @Override
        public ElliottScenarioSet getValue(final int index) {
            highestRequestedIndex.accumulateAndGet(index, Math::max);
            ElliottPhase phase = fixedPhase == null ? ElliottPhase.values()[1 + Math.floorMod(index, 5)] : fixedPhase;
            if (phase == ElliottPhase.NONE) {
                return ElliottScenarioSet.empty(index);
            }
            ElliottScenario scenario = ElliottScenario.builder()
                    .id("historical-" + index)
                    .currentPhase(phase)
                    .confidence(ElliottConfidence.zero(getBarSeries().numFactory()))
                    .degree(ElliottDegree.SUB_MINUETTE)
                    .type(ScenarioType.IMPULSE)
                    .startIndex(index)
                    .bullishDirection(true)
                    .build();
            return ElliottScenarioSet.of(List.of(scenario), index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }
}
