/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ForecastTest {

    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    void forecastSummarizesSamplesWithInterpolatedQuantiles() {
        Forecast<Num> forecast = Forecast.ofSamples(4, 2,
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
        Forecast<Num> forecast = Forecast.ofSamples(2, 1, List.of(NaN.NaN, NUM_FACTORY.numOf(3), NUM_FACTORY.numOf(1)),
                List.of(0.95, 0.05));

        assertThat(forecast.sampleCount()).isEqualTo(2);
        assertThat(forecast.quantiles().keySet()).containsExactly(0.05, 0.95);
        assertThat(forecast.median()).isEqualByComparingTo(NUM_FACTORY.numOf(2));
    }

    @Test
    void forecastMapsSummaryValues() {
        Forecast<Num> forecast = Forecast.ofSamples(2, 1, List.of(NUM_FACTORY.numOf(1), NUM_FACTORY.numOf(3)),
                List.of(0.5));

        Forecast<Num> doubled = forecast.map(value -> value.multipliedBy(NUM_FACTORY.numOf(2)));

        assertThat(doubled.mean()).isEqualByComparingTo(NUM_FACTORY.numOf(4));
        assertThat(doubled.median()).isEqualByComparingTo(NUM_FACTORY.numOf(4));
        assertThat(doubled.quantile(0.5)).isEqualByComparingTo(NUM_FACTORY.numOf(4));
        assertThat(doubled.decisionIndex()).isEqualTo(forecast.decisionIndex());
        assertThat(doubled.horizon()).isEqualTo(forecast.horizon());
    }

    @Test
    void forecastRejectsInvalidNumericValuesProducedByMapping() {
        Forecast<Num> forecast = Forecast.ofSamples(2, 1, List.of(NUM_FACTORY.one()), List.of(0.5));

        assertThrows(IllegalArgumentException.class, () -> forecast.map(value -> NaN.NaN));
    }

    @Test
    void forecastCreatesDefensiveSortedSummary() {
        Map<Double, Num> inputQuantiles = new LinkedHashMap<>();
        inputQuantiles.put(0.95, NUM_FACTORY.numOf(5));
        inputQuantiles.put(0.05, NUM_FACTORY.numOf(1));

        Forecast<Num> forecast = Forecast.ofSummary(7, 3, 20, NUM_FACTORY.numOf(3), NUM_FACTORY.numOf(2.5),
                NUM_FACTORY.numOf(1.25), inputQuantiles);
        inputQuantiles.clear();

        assertThat(forecast.isStable()).isTrue();
        assertThat(forecast.decisionIndex()).isEqualTo(7);
        assertThat(forecast.horizon()).isEqualTo(3);
        assertThat(forecast.sampleCount()).isEqualTo(20);
        assertThat(forecast.quantiles().keySet()).containsExactly(0.05, 0.95);
        assertThat(forecast.quantile(0.95)).isEqualByComparingTo(NUM_FACTORY.numOf(5));
        assertThrows(UnsupportedOperationException.class, () -> forecast.quantiles().put(0.5, NUM_FACTORY.numOf(2.5)));
    }

    @Test
    void forecastRejectsInvalidNumericSummaryValues() {
        Num infinity = NUM_FACTORY.numOf(Double.POSITIVE_INFINITY);

        assertThrows(IllegalArgumentException.class,
                () -> Forecast.ofSummary(0, 1, 1, NaN.NaN, NUM_FACTORY.zero(), NUM_FACTORY.zero(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> Forecast.ofSummary(0, 1, 1, NUM_FACTORY.zero(), infinity, NUM_FACTORY.zero(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> Forecast.ofSummary(0, 1, 1, NUM_FACTORY.zero(),
                NUM_FACTORY.zero(), NUM_FACTORY.numOf(-1), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> Forecast.ofSummary(0, 1, 1, NUM_FACTORY.zero(),
                NUM_FACTORY.zero(), NUM_FACTORY.zero(), Map.of(0.5, NaN.NaN)));
    }

    @Test
    void forecastAcceptsFiniteDecimalValuesThatOverflowDouble() {
        Num largeFiniteValue = DecimalNumFactory.getInstance().numOf("1E+10000");

        Forecast<Num> forecast = Forecast.ofSummary(0, 1, 1, largeFiniteValue, largeFiniteValue,
                DecimalNumFactory.getInstance().zero(), Map.of(0.5, largeFiniteValue));

        assertThat(forecast.isStable()).isTrue();
        assertThat(forecast.mean()).isSameAs(largeFiniteValue);
    }

    @Test
    void forecastReturnsNullForMissingQuantiles() {
        Forecast<Num> forecast = Forecast.ofSamples(2, 1, List.of(NUM_FACTORY.numOf(1), NUM_FACTORY.numOf(3)),
                List.of(0.5));

        assertThat(forecast.hasQuantile(0.95)).isFalse();
        assertThat(forecast.quantile(0.95)).isNull();
    }

    @Test
    void forecastValidatesInputs() {
        assertThrows(IllegalArgumentException.class, () -> Forecast.unstable(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Forecast.ofSamples(0, 1, List.of(NUM_FACTORY.one()), List.of(-0.1)));
        assertThrows(IllegalArgumentException.class,
                () -> Forecast.ofSamples(0, 1, List.of(NUM_FACTORY.one()), List.of()));
        assertThrows(IllegalArgumentException.class, () -> Forecast.ofSummary(0, 1, 0, NUM_FACTORY.zero(),
                NUM_FACTORY.zero(), NUM_FACTORY.zero(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> Forecast.ofSummary(0, 1, 1, NUM_FACTORY.zero(),
                NUM_FACTORY.zero(), NUM_FACTORY.zero(), Map.of(1.1, NUM_FACTORY.zero())));
        Forecast<Num> forecast = Forecast.ofSamples(0, 1, List.of(NUM_FACTORY.one()), List.of(0.5));
        assertThrows(IllegalArgumentException.class, () -> forecast.hasQuantile(-0.1));
        assertThrows(IllegalArgumentException.class, () -> forecast.quantile(1.1));
    }
}
