/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.EmpiricalElliottWaveForecastIndicator;
import org.ta4j.core.indicators.elliott.EmpiricalElliottWaveForecastIndicator.WaveForecast;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

class HighRewardElliottWaveStrategyTest {

    @Test
    void builtInForecastCanReachStrategyEntryPhasesOnIncludedFiveMinuteSeries() throws Exception {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("Binance-ETH-USD-PT5M-20230313_20230315.json")) {
            assertTrue(stream != null, "bundled five-minute series must be available");
            BarSeries series = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(stream);
            EmpiricalElliottWaveForecastIndicator indicator = new EmpiricalElliottWaveForecastIndicator(series);
            boolean sawEntryPhase = false;
            for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
                WaveForecast value = indicator.getValue(index);
                if (value.isStable() && List.of(ElliottPhase.WAVE1, ElliottPhase.WAVE2, ElliottPhase.WAVE4)
                        .contains(value.mostLikelyPhase())) {
                    sawEntryPhase = true;
                    break;
                }
            }
            assertTrue(sawEntryPhase,
                    "the built-in forecast must expose at least one phase that the strategy can enter");
        }
    }

    @Test
    void exposesOnlyTypedConstructionAndNoLegacyLabelBridge() {
        BarSeries series = series(100.0d, 101.0d, 102.0d);
        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series);

        assertEquals("HighRewardElliottWaveStrategy", strategy.getName());
        assertTrue(Arrays.stream(HighRewardElliottWaveStrategy.class.getConstructors())
                .map(Constructor::getParameterTypes)
                .noneMatch(types -> types.length == 2 && types[1] == String[].class));
    }

    @Test
    void entersAtConfirmedTurnsInWavesOneTwoAndFour() {
        for (ElliottPhase phase : List.of(ElliottPhase.WAVE1, ElliottPhase.WAVE2, ElliottPhase.WAVE4)) {
            BarSeries series = series(100.0d, 99.0d, 101.0d);
            int index = series.getEndIndex();
            FixedForecastIndicator forecast = new FixedForecastIndicator(series,
                    Map.of(index, stableForecast(series, index, phase, 0.75d)));
            HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, settings(), forecast);

            assertTrue(strategy.getEntryRule().isSatisfied(index, new BaseTradingRecord()), phase.name());
        }
    }

    @Test
    void doesNotEnterWithoutHistoricalStructureOrUpwardTurn() {
        BarSeries falling = series(100.0d, 102.0d, 101.0d);
        int index = falling.getEndIndex();
        FixedForecastIndicator stable = new FixedForecastIndicator(falling,
                Map.of(index, stableForecast(falling, index, ElliottPhase.WAVE2, 0.80d)));
        HighRewardElliottWaveStrategy fallingStrategy = new HighRewardElliottWaveStrategy(falling, settings(), stable);
        assertFalse(fallingStrategy.getEntryRule().isSatisfied(index, new BaseTradingRecord()));

        BarSeries rising = series(100.0d, 99.0d, 101.0d);
        FixedForecastIndicator unstable = new FixedForecastIndicator(rising, Map.of());
        HighRewardElliottWaveStrategy noStructure = new HighRewardElliottWaveStrategy(rising, settings(), unstable);
        assertFalse(noStructure.getEntryRule().isSatisfied(rising.getEndIndex(), new BaseTradingRecord()));
    }

    @Test
    void doesNotTreatMidWaveUpwardDriftAsABottom() {
        BarSeries series = series(98.0d, 99.0d, 100.0d);
        int index = series.getEndIndex();
        FixedForecastIndicator forecast = new FixedForecastIndicator(series,
                Map.of(index, stableForecast(series, index, ElliottPhase.WAVE2, 0.80d)));
        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, settings(), forecast);

        assertFalse(strategy.getEntryRule().isSatisfied(index, new BaseTradingRecord()));
    }

    @Test
    void exitsAtConfirmedWaveOneThreeAndFivePeaks() {
        for (ElliottPhase phase : List.of(ElliottPhase.WAVE1, ElliottPhase.WAVE3, ElliottPhase.WAVE5)) {
            BarSeries series = series(100.0d, 103.0d, 102.0d);
            int index = series.getEndIndex();
            FixedForecastIndicator forecast = new FixedForecastIndicator(series,
                    Map.of(index - 1, stableForecast(series, index - 1, phase, 0.80d), index,
                            stableForecast(series, index, phase, 0.80d)));
            HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, settings(), forecast);

            assertTrue(strategy.getExitRule().isSatisfied(index, openRecord(series, index - 1)), phase.name());
        }
    }

    @Test
    void doesNotTreatMidWaveDownwardDriftAsAPeak() {
        BarSeries series = series(104.0d, 103.0d, 102.0d);
        int index = series.getEndIndex();
        FixedForecastIndicator forecast = new FixedForecastIndicator(series,
                Map.of(index - 1, stableForecast(series, index - 1, ElliottPhase.WAVE3, 0.80d), index,
                        stableForecast(series, index, ElliottPhase.WAVE3, 0.80d)));
        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, settings(), forecast);

        assertFalse(strategy.getExitRule().isSatisfied(index, openRecord(series, index - 1)));
    }

    @Test
    void doesNotTreatAnUnavailableForecastAsAPeakTransition() {
        BarSeries series = series(100.0d, 101.0d, 102.0d);
        int index = series.getEndIndex();
        FixedForecastIndicator forecast = new FixedForecastIndicator(series,
                Map.of(index - 1, stableForecast(series, index - 1, ElliottPhase.WAVE3, 0.80d)));
        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, settings(), forecast);

        assertFalse(strategy.getExitRule().isSatisfied(index, openRecord(series, index - 1)));
    }

    @Test
    void exitsWhenAStableForecastTransitionsFromAPeakPhase() {
        BarSeries series = series(100.0d, 101.0d, 102.0d);
        int index = series.getEndIndex();
        FixedForecastIndicator forecast = new FixedForecastIndicator(series,
                Map.of(index - 1, stableForecast(series, index - 1, ElliottPhase.WAVE3, 0.80d), index,
                        stableForecast(series, index, ElliottPhase.WAVE4, 0.80d)));
        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, settings(), forecast);

        assertTrue(strategy.getExitRule().isSatisfied(index, openRecord(series, index - 1)));
    }

    @Test
    void exitsOnCompositeFixedRiskEvenOutsidePeakPhase() {
        BarSeries series = series(100.0d, 100.0d, 97.0d);
        int index = series.getEndIndex();
        FixedForecastIndicator forecast = new FixedForecastIndicator(series,
                Map.of(index - 1, stableForecast(series, index - 1, ElliottPhase.WAVE2, 0.80d), index,
                        stableForecast(series, index, ElliottPhase.WAVE2, 0.80d)));
        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, settings(), forecast);

        assertTrue(strategy.getExitRule().isSatisfied(index, openRecord(series, index - 1)));
    }

    @Test
    void validatesTypedSettings() {
        EmpiricalElliottWaveForecastIndicator.Settings forecast = new EmpiricalElliottWaveForecastIndicator.Settings(
                ElliottDegree.SUB_MINUETTE, 100, 10, 3, 5.0d, 0.2d);
        assertThrows(IllegalArgumentException.class,
                () -> new HighRewardElliottWaveStrategy.Settings(forecast, 0.0d, 1.0d, 2.0d, 1.0d, 10, 14, 2.0d, 20));
    }

    private static HighRewardElliottWaveStrategy.Settings settings() {
        EmpiricalElliottWaveForecastIndicator.Settings forecast = new EmpiricalElliottWaveForecastIndicator.Settings(
                ElliottDegree.SUB_MINUETTE, 100, 10, 3, 100.0d, 0.0d);
        return new HighRewardElliottWaveStrategy.Settings(forecast, 0.60d, 1.5d, 10.0d, 5.0d, 20, 14, 10.0d, 100);
    }

    private static WaveForecast stableForecast(final BarSeries series, final int index, final ElliottPhase phase,
            final double probability) {
        ElliottPhase alternative = phase == ElliottPhase.WAVE1 ? ElliottPhase.WAVE2 : ElliottPhase.WAVE1;
        int sampleCount = 100;
        int modalSamples = (int) Math.round(probability * sampleCount);
        EnumMap<ElliottPhase, Num> probabilities = new EnumMap<>(ElliottPhase.class);
        for (ElliottPhase candidate : List.of(ElliottPhase.WAVE1, ElliottPhase.WAVE2, ElliottPhase.WAVE3,
                ElliottPhase.WAVE4, ElliottPhase.WAVE5)) {
            double candidateProbability = candidate == phase ? probability
                    : candidate == alternative ? 1.0d - probability : 0.0d;
            probabilities.put(candidate, series.numFactory().numOf(candidateProbability));
        }
        List<Num> samples = new java.util.ArrayList<>(sampleCount);
        for (int sample = 0; sample < sampleCount; sample++) {
            ElliottPhase samplePhase = sample < modalSamples ? phase : alternative;
            samples.add(series.numFactory().numOf(samplePhase.impulseIndex()));
        }
        Forecast summary = Forecast.ofSamples(index, 1, samples);
        return new WaveForecast(summary, probabilities, phase, probabilities.get(phase));
    }

    private static TradingRecord openRecord(final BarSeries series, final int entryIndex) {
        BaseTradingRecord record = new BaseTradingRecord();
        record.enter(entryIndex, series.getBar(entryIndex).getClosePrice(), series.numFactory().one());
        return record;
    }

    private static BarSeries series(final double... closes) {
        BarSeries series = new MockBarSeriesBuilder().build();
        for (double close : closes) {
            series.barBuilder()
                    .openPrice(close)
                    .highPrice(close + 0.5d)
                    .lowPrice(close - 0.5d)
                    .closePrice(close)
                    .volume(100.0d)
                    .add();
        }
        return series;
    }

    private static final class FixedForecastIndicator extends AbstractIndicator<WaveForecast> {

        private final Map<Integer, WaveForecast> values;

        private FixedForecastIndicator(final BarSeries series, final Map<Integer, WaveForecast> values) {
            super(series);
            this.values = Map.copyOf(values);
        }

        @Override
        public WaveForecast getValue(final int index) {
            return values.getOrDefault(index, WaveForecast.unstable(index));
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }
}
