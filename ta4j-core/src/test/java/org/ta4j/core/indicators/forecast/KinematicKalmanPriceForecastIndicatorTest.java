/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.KinematicKalmanFilterIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastSupport;
import org.ta4j.core.indicators.forecast.state.KinematicKalmanForecastState;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.serialization.IndicatorSerialization;

public class KinematicKalmanPriceForecastIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Forecast> {

    public KinematicKalmanPriceForecastIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void projectsMeanAndObservedVarianceForAnyPositiveHorizon() {
        BarSeries series = series(10, 11, 13, 16);
        KinematicKalmanForecastStateIndicator stateIndicator = new KinematicKalmanForecastStateIndicator(
                new ClosePriceIndicator(series), 0.1, 0.2);
        KinematicKalmanForecastState state = stateIndicator.getValue(3);
        KinematicKalmanPriceForecastIndicator forecastIndicator = new KinematicKalmanPriceForecastIndicator(
                stateIndicator, 3);

        Forecast forecast = forecastIndicator.getValue(3);
        Num horizon = numOf(3);
        Num expectedMean = state.position().plus(state.velocity().multipliedBy(horizon));
        Num expectedVariance = state.positionVariance()
                .plus(state.positionVelocityCovariance().multipliedBy(numOf(6)))
                .plus(state.velocityVariance().multipliedBy(numOf(9)))
                .plus(state.processNoise().multipliedBy(numOf(8)))
                .plus(state.measurementNoise());

        assertTrue(forecast.isStable());
        assertEquals(3, forecast.decisionIndex());
        assertEquals(3, forecast.horizon());
        assertEquals(expectedMean.doubleValue(), forecast.mean().doubleValue(), 1e-12);
        assertEquals(expectedVariance.sqrt().doubleValue(), forecast.standardDeviation().doubleValue(), 1e-12);
        assertEquals(ForecastSupport.analytic("linear-gaussian-kalman-observation"), forecast.support());
        assertEquals(Forecast.DEFAULT_QUANTILE_PROBABILITIES, forecast.quantiles().keySet().stream().toList());
        NormalDistribution standardNormal = new NormalDistribution(0d, 1d);
        for (double probability : Forecast.DEFAULT_QUANTILE_PROBABILITIES) {
            double expectedQuantile = forecast.mean().doubleValue() + forecast.standardDeviation().doubleValue()
                    * standardNormal.inverseCumulativeProbability(probability);
            assertEquals(expectedQuantile, forecast.quantile(probability).doubleValue(), 1e-12);
        }
    }

    @Test
    public void forecastAtDecisionIndexDoesNotReadFutureBars() {
        BarSeries prefix = series(10, 11, 13, 16);
        BarSeries extended = series(10, 11, 13, 16, 1_000, -1_000);
        Forecast prefixForecast = new KinematicKalmanFilterIndicator(new ClosePriceIndicator(prefix)).forecast(2)
                .getValue(3);
        Forecast extendedForecast = new KinematicKalmanFilterIndicator(new ClosePriceIndicator(extended)).forecast(2)
                .getValue(3);

        assertEquals(prefixForecast.mean().doubleValue(), extendedForecast.mean().doubleValue(), 1e-12);
        assertEquals(prefixForecast.standardDeviation().doubleValue(),
                extendedForecast.standardDeviation().doubleValue(), 1e-12);
        assertEquals(prefixForecast.quantiles(), extendedForecast.quantiles());
    }

    @Test
    public void composesWithForwardAndConformalForecastIndicators() {
        double[] prices = new double[45];
        for (int index = 0; index < prices.length; index++) {
            prices[index] = 100 + index;
        }
        BarSeries series = series(prices);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        KinematicKalmanPriceForecastIndicator base = new KinematicKalmanFilterIndicator(close).forecast(2);
        Indicator<Num> point = base.mean();
        RollingConformalForecastProjectionIndicator calibrated = RollingConformalForecastProjectionIndicator
                .builder(base, close)
                .targetCoverage(0.5)
                .calibrationWindow(10)
                .minimumCalibrationCount(5)
                .build();

        Forecast baseForecast = base.getValue(40);
        Forecast calibratedForecast = calibrated.getValue(40);

        assertEquals(baseForecast.mean(), point.getValue(40));
        assertTrue(calibratedForecast.isStable());
        assertEquals(baseForecast.mean(), calibratedForecast.mean());
        assertEquals(baseForecast.support(), calibratedForecast.support());
        assertTrue(calibratedForecast.quantile(0.05).isLessThanOrEqual(baseForecast.quantile(0.05)));
        assertTrue(calibratedForecast.quantile(0.95).isGreaterThanOrEqual(baseForecast.quantile(0.95)));
    }

    @Test
    public void unavailableStateProducesUnavailableForecast() {
        BarSeries series = series(0, 11);
        FixedIndicator<Num> source = new FixedIndicator<>(series, NaN.NaN, numOf(11));
        KinematicKalmanPriceForecastIndicator forecast = new KinematicKalmanFilterIndicator(source).forecast(2);

        assertFalse(forecast.getValue(0).isStable());
        assertTrue(forecast.getValue(0).mean().isNaN());
        assertTrue(forecast.getValue(1).isStable());
    }

    @Test
    public void validatesStateAndHorizon() {
        BarSeries series = series(10);
        KinematicKalmanForecastStateIndicator state = new KinematicKalmanForecastStateIndicator(
                new ClosePriceIndicator(series));

        assertThrows(NullPointerException.class, () -> new KinematicKalmanPriceForecastIndicator(null));
        assertThrows(IllegalArgumentException.class, () -> new KinematicKalmanPriceForecastIndicator(state, -1));
    }

    @Test
    public void oneBarConstructorAndUnstableCountDelegateToState() {
        BarSeries series = series(10, 11);
        KinematicKalmanForecastStateIndicator state = new KinematicKalmanForecastStateIndicator(
                new ClosePriceIndicator(series));
        KinematicKalmanPriceForecastIndicator forecast = new KinematicKalmanPriceForecastIndicator(state);

        assertEquals(1, forecast.getHorizon());
        assertEquals(state.getCountOfUnstableBars(), forecast.getCountOfUnstableBars());
    }

    @Test
    public void removedDecisionIndexIsUnavailableWithRequestedMetadata() {
        BarSeries series = series(10, 11, 12, 13);
        KinematicKalmanPriceForecastIndicator forecast = new KinematicKalmanFilterIndicator(
                new ClosePriceIndicator(series)).forecast(2);
        series.setMaximumBarCount(2);

        Forecast removed = forecast.getValue(1);

        assertFalse(removed.isStable());
        assertEquals(1, removed.decisionIndex());
        assertEquals(2, removed.horizon());
    }

    @Test
    public void primitiveOverflowProducesUnavailableForecast() {
        BarSeries series = series(10);
        KinematicKalmanForecastStateIndicator state = new KinematicKalmanForecastStateIndicator(
                new ClosePriceIndicator(series), Double.MAX_VALUE / 2, 0.2);
        Forecast forecast = new KinematicKalmanPriceForecastIndicator(state, 3).getValue(0);

        if (numFactory == DoubleNumFactory.getInstance()) {
            assertFalse(forecast.isStable());
        } else {
            assertTrue(forecast.isStable());
        }
    }

    @Test
    public void descriptorAndJsonRoundTrip() {
        BarSeries series = series(10, 11, 12, 13);
        KinematicKalmanPriceForecastIndicator original = new KinematicKalmanFilterIndicator(
                new ClosePriceIndicator(series), 0.01, 0.1).forecast(3);

        Indicator<?> descriptorCopy = IndicatorSerialization.fromDescriptor(series, original.toDescriptor());
        Indicator<?> jsonCopy = Indicator.fromJson(series, original.toJson());

        assertEquals(original.toDescriptor(), descriptorCopy.toDescriptor());
        assertEquals(original.toDescriptor(), jsonCopy.toDescriptor());
        assertEquals(original.getValue(3).mean(), ((Forecast) descriptorCopy.getValue(3)).mean());
        assertEquals(original.getValue(3).mean(), ((Forecast) jsonCopy.getValue(3)).mean());
    }

    private BarSeries series(double... values) {
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(values).build();
    }
}
