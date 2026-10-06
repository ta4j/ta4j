/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.forecast.state.KinematicKalmanForecastState;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class AdaptiveKalmanNoiseExampleTest {

    private static final List<NumFactory> FACTORIES = List.of(DoubleNumFactory.getInstance(),
            DecimalNumFactory.getInstance());

    @Test
    void composesVarianceAndClippedRelativeVolumeInBothNumericFactories() {
        for (NumFactory factory : FACTORIES) {
            BarSeries series = flatSeries(factory, 9);
            AdaptiveKalmanNoiseExample.NoiseInputs noise = AdaptiveKalmanNoiseExample.createNoise(
                    values(series, 2, 2, 2, 2, 2, 2, 2, 2, 2), values(series, 0, 0, 0, 0, 0, 0, 0, 80, 0), 8);

            assertEquals(0, noise.relativeVolume().getCountOfUnstableBars());
            assertEquals(1, noise.relativeVolume().getValue(6).doubleValue(), 1e-12);
            assertEquals(4, noise.relativeVolume().getValue(7).doubleValue(), 1e-12);
            assertEquals(0.25, noise.relativeVolume().getValue(8).doubleValue(), 1e-12);
            assertEquals(4, noise.priceVariance().getValue(7).doubleValue(), 1e-12);
            assertEquals(0.04, noise.processNoise().getValue(7).doubleValue(), 1e-12);
            assertEquals(2, noise.measurementNoise().getValue(7).doubleValue(), 1e-12);
            assertEquals(8, noise.measurementNoise().getValue(8).doubleValue(), 1e-12);
        }
    }

    @Test
    void missingVolumeAndZeroBaselineAreNeutralWhileNegativeVolumeIsUnavailable() {
        for (NumFactory factory : FACTORIES) {
            BarSeries series = flatSeries(factory, 5);
            FixedIndicator<Num> volume = new FixedIndicator<>(series, factory.zero(), factory.zero(), NaN.NaN,
                    factory.numOf(10), factory.numOf(-1));
            AdaptiveKalmanNoiseExample.NoiseInputs noise = AdaptiveKalmanNoiseExample
                    .createNoise(values(series, 2, 2, 2, 2, 2), volume, 2);

            for (int index = 0; index < 4; index++) {
                assertEquals(1, noise.relativeVolume().getValue(index).doubleValue(), 1e-12);
                assertEquals(4, noise.measurementNoise().getValue(index).doubleValue(), 1e-12);
            }
            assertTrue(noise.relativeVolume().getValue(4).isNaN());
            assertTrue(noise.measurementNoise().getValue(4).isNaN());
        }
    }

    @Test
    void negativeHistoricalVolumeUsesNeutralConfidenceUntilItLeavesTheWindow() {
        for (NumFactory factory : FACTORIES) {
            BarSeries series = flatSeries(factory, 4);
            AdaptiveKalmanNoiseExample.NoiseInputs noise = AdaptiveKalmanNoiseExample
                    .createNoise(values(series, 2, 2, 2, 2), values(series, 10, -1, 10, 30), 2);

            // A cold tail read and reverse reads must follow the same window policy.
            assertEquals(1.5, noise.relativeVolume().getValue(3).doubleValue(), 1e-12);
            assertEquals(4 / Math.sqrt(1.5), noise.measurementNoise().getValue(3).doubleValue(), 1e-12);
            assertEquals(1, noise.relativeVolume().getValue(2).doubleValue(), 1e-12);
            assertEquals(4, noise.measurementNoise().getValue(2).doubleValue(), 1e-12);
            assertTrue(noise.measurementNoise().getValue(1).isNaN());
        }
    }

    @Test
    void varianceFloorDoesNotReplaceUnavailableAtr() {
        for (NumFactory factory : FACTORIES) {
            BarSeries series = flatSeries(factory, 3);
            FixedIndicator<Num> atr = new FixedIndicator<>(series, NaN.NaN, factory.zero(), factory.two());
            AdaptiveKalmanNoiseExample.NoiseInputs noise = AdaptiveKalmanNoiseExample.createNoise(atr,
                    new VolumeIndicator(series), 2);

            assertTrue(noise.processNoise().getValue(0).isNaN());
            assertTrue(noise.measurementNoise().getValue(0).isNaN());
            assertEquals(1e-10, noise.processNoise().getValue(1).doubleValue(), 1e-22);
            assertEquals(1e-8, noise.measurementNoise().getValue(1).doubleValue(), 1e-20);
        }
    }

    @Test
    void realAtrWarmupAndLagSeedTheFirstUsableMeasurementWithoutVelocity() {
        for (NumFactory factory : FACTORIES) {
            BarSeries series = flatSeries(factory, 6);
            AdaptiveKalmanNoiseExample.Model sameBar = AdaptiveKalmanNoiseExample.createModels(series, 2, 3, false)
                    .get(2);
            AdaptiveKalmanNoiseExample.Model lagged = AdaptiveKalmanNoiseExample.createModels(series, 2, 3, true)
                    .get(2);
            sameBar.state().getValue(series.getEndIndex());
            lagged.state().getValue(series.getEndIndex());

            assertEquals(2, sameBar.state().getCountOfUnstableBars());
            assertEquals(3, lagged.state().getCountOfUnstableBars());
            assertFalse(sameBar.state().getValue(1).isStable());
            assertFalse(lagged.state().getValue(2).isStable());
            KinematicKalmanForecastState first = sameBar.state().getValue(2);
            KinematicKalmanForecastState laggedFirst = lagged.state().getValue(3);
            assertTrue(first.isStable());
            assertTrue(laggedFirst.isStable());
            assertEquals(factory.numOf(100), first.position());
            assertEquals(factory.zero(), first.velocity());
            assertEquals(1, first.observationCount());
            assertEquals(first.position(), laggedFirst.position());
            assertEquals(first.velocity(), laggedFirst.velocity());
            assertEquals(first.processNoise(), laggedFirst.processNoise());
            assertEquals(first.measurementNoise(), laggedFirst.measurementNoise());
            assertEquals(1, laggedFirst.observationCount());
        }
    }

    @Test
    void comparesTheSameOriginsAndScoresFuturePricesRatherThanSameBarFit() {
        for (NumFactory factory : FACTORIES) {
            BarSeries flat = flatSeries(factory, 6);
            AdaptiveKalmanNoiseExample.Evaluation common = AdaptiveKalmanNoiseExample.evaluate(
                    AdaptiveKalmanNoiseExample.createModels(flat, 2, 3, false), new ClosePriceIndicator(flat), 5);
            assertEquals(3, common.sampleCount());
            assertEquals(2, common.skippedCount());
            assertEquals(4, common.scores().size());
            for (AdaptiveKalmanNoiseExample.Score score : common.scores()) {
                assertTrue(score.meanAbsoluteError().isZero());
                assertTrue(score.rootMeanSquaredError().isZero());
            }

            BarSeries jump = series(factory, 100, 100, 100, 110);
            AdaptiveKalmanNoiseExample.Evaluation nextBar = AdaptiveKalmanNoiseExample.evaluate(
                    AdaptiveKalmanNoiseExample.createModels(jump, 2, 3, false), new ClosePriceIndicator(jump), 1);
            assertEquals(1, nextBar.sampleCount());
            assertEquals(0, nextBar.skippedCount());
            for (AdaptiveKalmanNoiseExample.Score score : nextBar.scores()) {
                assertEquals(10, score.meanAbsoluteError().doubleValue(), 1e-12);
                assertEquals(10, score.rootMeanSquaredError().doubleValue(), 1e-12);
            }
        }
    }

    @Test
    void noCommonSamplesProduceUnavailableMetricsRatherThanPerfectScores() {
        BarSeries series = flatSeries(DoubleNumFactory.getInstance(), 2);
        List<AdaptiveKalmanNoiseExample.Model> models = AdaptiveKalmanNoiseExample.createModels(series, 2, 3, false);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        AdaptiveKalmanNoiseExample.Evaluation evaluation = AdaptiveKalmanNoiseExample.evaluate(models, close, 10);

        assertEquals(0, evaluation.sampleCount());
        assertEquals(1, evaluation.skippedCount());
        for (AdaptiveKalmanNoiseExample.Score score : evaluation.scores()) {
            assertTrue(score.meanAbsoluteError().isNaN());
            assertTrue(score.rootMeanSquaredError().isNaN());
        }
        assertThrows(IllegalArgumentException.class, () -> AdaptiveKalmanNoiseExample.evaluate(models, close, 0));
        assertThrows(IllegalArgumentException.class, () -> AdaptiveKalmanNoiseExample
                .createNoise(new ATRIndicator(series, 2), new VolumeIndicator(series), 0));
    }

    private static FixedIndicator<Num> values(BarSeries series, double... values) {
        Num[] numbers = Arrays.stream(values).mapToObj(series.numFactory()::numOf).toArray(Num[]::new);
        return new FixedIndicator<>(series, numbers);
    }

    private static BarSeries flatSeries(NumFactory factory, int count) {
        double[] closes = new double[count];
        Arrays.fill(closes, 100);
        return series(factory, closes);
    }

    private static BarSeries series(NumFactory factory, double... closes) {
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(factory).build();
        Instant firstEnd = Instant.parse("2024-01-01T00:01:00Z");
        for (int index = 0; index < closes.length; index++) {
            double close = closes[index];
            series.barBuilder()
                    .timePeriod(Duration.ofMinutes(1))
                    .endTime(firstEnd.plusSeconds(60L * index))
                    .openPrice(close)
                    .highPrice(close + 1)
                    .lowPrice(close - 1)
                    .closePrice(close)
                    .volume(10)
                    .add();
        }
        return series;
    }
}
