/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class PredictionSnapshotTest {

    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    void forecastSummarizesSamplesWithInterpolatedQuantiles() {
        PredictionSnapshot.Forecast<Num> forecast = PredictionSnapshot.Forecast.ofSamples(4, 2,
                List.of(NUM_FACTORY.numOf(1), NUM_FACTORY.numOf(2), NUM_FACTORY.numOf(3), NUM_FACTORY.numOf(4)),
                List.of(0.25, 0.5, 0.75));

        assertThat(forecast.isStable()).isTrue();
        assertThat(forecast.sampleCount()).isEqualTo(4);
        assertThat(forecast.mean()).isEqualByComparingTo(NUM_FACTORY.numOf(2.5));
        assertThat(forecast.median()).isEqualByComparingTo(NUM_FACTORY.numOf(2.5));
        assertThat(forecast.standardDeviation()).isEqualByComparingTo(NUM_FACTORY.numOf(Math.sqrt(1.25)));
        assertThat(forecast.hasQuantile(0.25)).isTrue();
        assertThat(forecast.quantile(0.25)).isEqualByComparingTo(NUM_FACTORY.numOf(1.75));
        assertThat(forecast.quantile(0.75)).isEqualByComparingTo(NUM_FACTORY.numOf(3.25));
    }

    @Test
    void forecastSkipsInvalidSamplesAndSortsQuantiles() {
        PredictionSnapshot.Forecast<Num> forecast = PredictionSnapshot.Forecast.ofSamples(2, 1,
                List.of(NaN.NaN, NUM_FACTORY.numOf(3), NUM_FACTORY.numOf(1)), List.of(0.95, 0.05));

        assertThat(forecast.sampleCount()).isEqualTo(2);
        assertThat(forecast.quantiles().keySet()).containsExactly(0.05, 0.95);
        assertThat(forecast.median()).isEqualByComparingTo(NUM_FACTORY.numOf(2));
    }

    @Test
    void forecastMapsSummaryValues() {
        PredictionSnapshot.Forecast<Num> forecast = PredictionSnapshot.Forecast.ofSamples(2, 1,
                List.of(NUM_FACTORY.numOf(1), NUM_FACTORY.numOf(3)), List.of(0.5));

        PredictionSnapshot.Forecast<Num> doubled = forecast.map(value -> value.multipliedBy(NUM_FACTORY.numOf(2)));

        assertThat(doubled.mean()).isEqualByComparingTo(NUM_FACTORY.numOf(4));
        assertThat(doubled.median()).isEqualByComparingTo(NUM_FACTORY.numOf(4));
        assertThat(doubled.quantile(0.5)).isEqualByComparingTo(NUM_FACTORY.numOf(4));
        assertThat(doubled.decisionIndex()).isEqualTo(forecast.decisionIndex());
        assertThat(doubled.horizon()).isEqualTo(forecast.horizon());
    }

    @Test
    void forecastReturnsNullForMissingQuantiles() {
        PredictionSnapshot.Forecast<Num> forecast = PredictionSnapshot.Forecast.ofSamples(2, 1,
                List.of(NUM_FACTORY.numOf(1), NUM_FACTORY.numOf(3)), List.of(0.5));

        assertThat(forecast.hasQuantile(0.95)).isFalse();
        assertThat(forecast.quantile(0.95)).isNull();
    }

    @Test
    void forecastValidatesInputs() {
        assertThrows(IllegalArgumentException.class, () -> PredictionSnapshot.Forecast.unstable(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> PredictionSnapshot.Forecast.ofSamples(0, 1, List.of(NUM_FACTORY.one()), List.of(-0.1)));
        assertThrows(IllegalArgumentException.class,
                () -> PredictionSnapshot.Forecast.ofSamples(0, 1, List.of(NUM_FACTORY.one()), List.of()));
        PredictionSnapshot.Forecast<Num> forecast = PredictionSnapshot.Forecast.ofSamples(0, 1,
                List.of(NUM_FACTORY.one()), List.of(0.5));
        assertThrows(IllegalArgumentException.class, () -> forecast.hasQuantile(-0.1));
        assertThrows(IllegalArgumentException.class, () -> forecast.quantile(1.1));
    }
}
