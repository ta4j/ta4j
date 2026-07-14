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
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ForecastTest {

    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    void summarizesEmpiricalSamplesWithInterpolatedQuantiles() {
        Forecast forecast = Forecast.ofSamples(4, 2,
                List.of(NUM_FACTORY.numOf(1), NUM_FACTORY.numOf(2), NUM_FACTORY.numOf(3), NUM_FACTORY.numOf(4)),
                List.of(0.25, 0.5, 0.75));

        assertThat(forecast.support()).isEqualTo(ForecastSupport.empirical(4));
        assertThat(forecast.sampleCount()).isEqualTo(4);
        assertThat(forecast.mean()).isEqualByComparingTo(NUM_FACTORY.numOf(2.5));
        assertThat(forecast.median()).isEqualByComparingTo(NUM_FACTORY.numOf(2.5));
        assertThat(forecast.standardDeviation()).isEqualByComparingTo(NUM_FACTORY.numOf(Math.sqrt(1.25)));
        assertThat(forecast.quantile(0.25)).isEqualByComparingTo(NUM_FACTORY.numOf(1.75));
        assertThat(forecast.quantile(0.75)).isEqualByComparingTo(NUM_FACTORY.numOf(3.25));
    }

    @Test
    void skipsInvalidSamplesAndSortsQuantiles() {
        Forecast forecast = Forecast.ofSamples(2, 1, List.of(NaN.NaN, NUM_FACTORY.numOf(3), NUM_FACTORY.numOf(1)),
                List.of(0.95, 0.05));

        assertThat(forecast.sampleCount()).isEqualTo(2);
        assertThat(forecast.quantiles().keySet()).containsExactly(0.05, 0.95);
        assertThat(forecast.median()).isEqualByComparingTo(NUM_FACTORY.numOf(2));
    }

    @Test
    void builderCreatesDefensiveFactoryCoherentSummary() {
        Map<Double, Num> inputQuantiles = new LinkedHashMap<>();
        inputQuantiles.put(0.95, DecimalNumFactory.getInstance(40).numOf(5));
        inputQuantiles.put(0.05, DecimalNumFactory.getInstance(40).numOf(1));

        Forecast forecast = Forecast.builder(7, 3, NUM_FACTORY, ForecastSupport.empirical(20))
                .mean(NUM_FACTORY.numOf(3))
                .median(NUM_FACTORY.numOf(2.5))
                .standardDeviation(NUM_FACTORY.numOf(1.25))
                .quantiles(inputQuantiles)
                .build();
        inputQuantiles.clear();

        assertThat(forecast.quantiles().keySet()).containsExactly(0.05, 0.95);
        assertThat(forecast.quantile(0.95).getNumFactory()).isSameAs(NUM_FACTORY);
        assertThrows(UnsupportedOperationException.class, () -> forecast.quantiles().put(0.5, NUM_FACTORY.one()));
    }

    @Test
    void builderDistinguishesAnalyticSupportFromSamples() {
        Forecast forecast = Forecast.builder(0, 1, NUM_FACTORY, ForecastSupport.analytic("normal"))
                .mean(NUM_FACTORY.zero())
                .median(NUM_FACTORY.zero())
                .standardDeviation(NUM_FACTORY.one())
                .build();

        assertThat(forecast.isStable()).isTrue();
        assertThat(forecast.sampleCount()).isZero();
        assertThat(forecast.support()).isEqualTo(ForecastSupport.analytic("normal"));
        assertThat(Forecast.unstable(0, 1).support()).isEqualTo(ForecastSupport.unavailable());
    }

    @Test
    void builderRejectsIncoherentSummaries() {
        Forecast.Builder base = Forecast.builder(0, 1, NUM_FACTORY, ForecastSupport.empirical(2))
                .mean(NUM_FACTORY.one())
                .median(NUM_FACTORY.one());

        assertThrows(IllegalArgumentException.class, () -> base.standardDeviation(NUM_FACTORY.numOf(-1)).build());
        assertThrows(IllegalArgumentException.class,
                () -> Forecast.builder(0, 1, NUM_FACTORY, ForecastSupport.empirical(2))
                        .mean(NUM_FACTORY.one())
                        .median(NUM_FACTORY.one())
                        .standardDeviation(NUM_FACTORY.one())
                        .quantiles(Map.of(0.5, NUM_FACTORY.two()))
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> Forecast.builder(0, 1, NUM_FACTORY, ForecastSupport.empirical(2))
                        .mean(NUM_FACTORY.one())
                        .median(NUM_FACTORY.one())
                        .standardDeviation(NUM_FACTORY.one())
                        .quantiles(Map.of(0.05, NUM_FACTORY.two(), 0.95, NUM_FACTORY.one()))
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> Forecast.builder(0, 1, NUM_FACTORY, ForecastSupport.empirical(2))
                        .mean(NUM_FACTORY.one())
                        .median(NUM_FACTORY.two())
                        .standardDeviation(NUM_FACTORY.zero())
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> Forecast.builder(0, 1, NUM_FACTORY, ForecastSupport.empirical(1))
                        .mean(NUM_FACTORY.one())
                        .median(NUM_FACTORY.one())
                        .standardDeviation(NUM_FACTORY.one())
                        .build());
    }

    @Test
    void builderRejectsInvalidNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> Forecast.builder(0, 1, NUM_FACTORY, ForecastSupport.empirical(1))
                        .mean(NaN.NaN)
                        .median(NUM_FACTORY.zero())
                        .standardDeviation(NUM_FACTORY.zero())
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> Forecast.builder(0, 1, NUM_FACTORY, ForecastSupport.empirical(1))
                        .mean(NUM_FACTORY.zero())
                        .median(NUM_FACTORY.zero())
                        .standardDeviation(NUM_FACTORY.zero())
                        .quantiles(Map.of(0.5, NaN.NaN))
                        .build());
    }

    @Test
    void normalizationHonorsTargetDecimalPrecisionIncludingZero() {
        NumFactory lowPrecision = DecimalNumFactory.getInstance(3);
        NumFactory highPrecision = DecimalNumFactory.getInstance(40);
        Forecast forecast = Forecast.builder(0, 1, lowPrecision, ForecastSupport.analytic("normal"))
                .mean(highPrecision.numOf("1.234567890123456789"))
                .median(highPrecision.numOf("1.234567890123456789"))
                .standardDeviation(highPrecision.zero())
                .quantiles(Map.of(0.5, highPrecision.numOf("1.234567890123456789")))
                .build();

        assertThat(((DecimalNum) forecast.mean()).getMathContext().getPrecision()).isEqualTo(3);
        assertThat(((DecimalNum) forecast.standardDeviation()).getMathContext().getPrecision()).isEqualTo(3);
    }

    @Test
    void acceptsFiniteDecimalValuesThatOverflowPrimitiveDouble() {
        NumFactory decimalFactory = DecimalNumFactory.getInstance(40);
        Num largeFiniteValue = decimalFactory.numOf("1E+10000");

        Forecast forecast = Forecast.builder(0, 1, decimalFactory, ForecastSupport.analytic("external"))
                .mean(largeFiniteValue)
                .median(largeFiniteValue)
                .standardDeviation(decimalFactory.zero())
                .quantiles(Map.of(0.5, largeFiniteValue))
                .build();

        assertThat(forecast.mean()).isEqualByComparingTo(largeFiniteValue);
    }

    @Test
    void rejectsFactoryOverflowAndNonzeroUnderflow() {
        NumFactory decimalFactory = DecimalNumFactory.getInstance(40);
        Num overflow = decimalFactory.numOf("1E+10000");
        Num underflow = decimalFactory.numOf("1E-10000");

        assertThrows(IllegalArgumentException.class,
                () -> Forecast.builder(0, 1, NUM_FACTORY, ForecastSupport.analytic("external"))
                        .mean(overflow)
                        .median(overflow)
                        .standardDeviation(NUM_FACTORY.zero())
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> Forecast.builder(0, 1, NUM_FACTORY, ForecastSupport.analytic("external"))
                        .mean(underflow)
                        .median(underflow)
                        .standardDeviation(NUM_FACTORY.zero())
                        .build());
    }

    @Test
    void affineTransformsLocationDispersionAndNegativeQuantileOrder() {
        Forecast forecast = Forecast.ofSamples(2, 1, List.of(NUM_FACTORY.one(), NUM_FACTORY.numOf(3)),
                List.of(0.05, 0.5, 0.95));

        Forecast transformed = forecast.affine(NUM_FACTORY.numOf(-2), NUM_FACTORY.numOf(10));

        assertThat(transformed.mean()).isEqualByComparingTo(NUM_FACTORY.numOf(6));
        assertThat(transformed.median()).isEqualByComparingTo(NUM_FACTORY.numOf(6));
        assertThat(transformed.standardDeviation()).isEqualByComparingTo(NUM_FACTORY.numOf(2));
        assertThat(transformed.quantile(0.05)).isEqualByComparingTo(NUM_FACTORY.numOf(4.2));
        assertThat(transformed.quantile(0.95)).isEqualByComparingTo(NUM_FACTORY.numOf(7.8));
        assertThat(forecast.scale(NUM_FACTORY.two()).mean()).isEqualByComparingTo(NUM_FACTORY.numOf(4));
    }

    @Test
    void missingQuantilesUseNaN() {
        Forecast forecast = Forecast.ofSamples(2, 1, List.of(NUM_FACTORY.one(), NUM_FACTORY.numOf(3)), List.of(0.5));

        assertThat(forecast.hasQuantile(0.95)).isFalse();
        assertThat(forecast.quantile(0.95).isNaN()).isTrue();
    }

    @Test
    void validatesMetadataProbabilitiesAndSupport() {
        assertThrows(IllegalArgumentException.class, () -> Forecast.unstable(0, 0));
        assertThrows(IllegalArgumentException.class, () -> ForecastSupport.empirical(0));
        assertThrows(IllegalArgumentException.class, () -> ForecastSupport.analytic(" "));
        assertThrows(IllegalArgumentException.class,
                () -> Forecast.builder(0, 1, NUM_FACTORY, ForecastSupport.unavailable()));
        assertThrows(IllegalArgumentException.class,
                () -> Forecast.ofSamples(0, 1, List.of(NUM_FACTORY.one()), List.of(-0.1)));
        Forecast forecast = Forecast.ofSamples(0, 1, List.of(NUM_FACTORY.one()), List.of(0.5));
        assertThrows(IllegalArgumentException.class, () -> forecast.quantile(1.1));
    }
}
