/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.state.ForecastFeatureExtractors;
import org.ta4j.core.indicators.forecast.state.RoughVolatilityForecastState;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RoughVolatilityForecastStateIndicatorTest
        extends AbstractIndicatorTest<RoughVolatilityForecastStateIndicator, RoughVolatilityForecastState> {

    public RoughVolatilityForecastStateIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void constantReturnsProduceStableCollapsedVarianceTermStructure() {
        Fixture fixture = fixture(ReturnRepresentation.LOG, 0, 0, 0, 0, 0, 0, 0, 0);
        RoughVolatilityForecastStateIndicator indicator = configured(fixture.returns(), 3);

        RoughVolatilityForecastState state = indicator.getValue(7);

        assertSame(fixture.returns(), indicator.getReturnIndicator());
        assertEquals(ReturnRepresentation.LOG, indicator.getReturnRepresentation());
        assertEquals(3, indicator.getCountOfUnstableBars());
        assertTrue(state.isStable());
        assertNumEquals(0.01d, state.roughnessHurst());
        assertNumEquals(0d, state.volOfVol());
        assertEquals(3, state.horizonVarianceForecasts().size());
        state.horizonVarianceForecasts().forEach(value -> assertNumEquals(0d, value));
    }

    @Test
    public void variogramFixtureProducesDeterministicBoundedHurstAndCumulativeVariance() {
        double[] returns = new double[8];
        Arrays.setAll(returns, index -> Math.exp(-5d + 0.2d * index) - 1e-8d);
        Fixture fixture = fixture(ReturnRepresentation.LOG, returns);
        RoughVolatilityForecastStateIndicator indicator = RoughVolatilityForecastStateIndicator
                .builder(fixture.returns())
                .initializationBarCount(2)
                .decayFactor(0.5d)
                .driftMode(EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN)
                .roughnessWindow(6)
                .volOfVolWindow(4)
                .horizon(3)
                .build();

        RoughVolatilityForecastState state = indicator.getValue(7);
        double hurst = state.roughnessHurst().doubleValue();

        assertTrue(state.isStable());
        assertNumEquals(0.49d, state.roughnessHurst());
        assertTrue(state.volOfVol().isPositive());
        assertNumEquals(state.variance(), state.horizonVarianceForecasts().get(0));
        assertNumEquals(state.variance().doubleValue() * Math.pow(2d, 2d * hurst),
                state.horizonVarianceForecasts().get(1));
        assertNumEquals(state.variance().doubleValue() * Math.pow(3d, 2d * hurst),
                state.horizonVarianceForecasts().get(2));
    }

    @Test
    public void invalidReturnsRemainUnavailableUntilEveryRequiredWindowRecovers() {
        Fixture fixture = fixture(ReturnRepresentation.LOG, 0.01, 0.02, Double.NaN, 0.03, 0.04, 0.05, 0.06, 0.07);
        RoughVolatilityForecastStateIndicator indicator = configured(fixture.returns(), 2);

        assertTrue(indicator.getValue(2).roughnessHurst().isNaN());
        assertTrue(indicator.getValue(4).roughnessHurst().isNaN());
        assertTrue(indicator.getValue(5).roughnessHurst().isNaN());
        RoughVolatilityForecastState recovered = indicator.getValue(6);
        assertTrue(recovered.isStable());
        assertEquals(4, recovered.observationCount());
    }

    @Test
    public void futureSuffixDoesNotChangeEarlierState() {
        double[] prefix = { 0.01, 0.02, 0.04, 0.03, 0.08, 0.02, 0.12, 0.04 };
        Fixture prefixFixture = fixture(ReturnRepresentation.LOG, prefix);
        Fixture extendedFixture = fixture(ReturnRepresentation.LOG, 0.01, 0.02, 0.04, 0.03, 0.08, 0.02, 0.12, 0.04,
                -0.5, 0.7);

        RoughVolatilityForecastState prefixState = configured(prefixFixture.returns(), 3).getValue(7);
        RoughVolatilityForecastState extendedState = configured(extendedFixture.returns(), 3).getValue(7);

        assertEquals(prefixState, extendedState);
    }

    @Test
    public void specializedSchemaComposesWithAnalogProjection() {
        Fixture fixture = fixture(ReturnRepresentation.LOG, 0.01, 0.02, -0.01, 0.03, -0.02, 0.04, 0.01, 0.05, -0.01,
                0.02, 0.06, -0.03, 0.01, 0.04, -0.02, 0.03, 0.05, -0.01, 0.02, 0.04);
        RoughVolatilityForecastStateIndicator states = configured(fixture.returns(), 3);
        AnalogReturnProjectionIndicator<RoughVolatilityForecastState> projection = AnalogReturnProjectionIndicator
                .builder(states)
                .featureExtractor(ForecastFeatureExtractors.roughVolatility())
                .lookbackBarCount(10)
                .neighborCount(3)
                .minimumNeighborCount(2)
                .build();

        Forecast forecast = projection.getValue(19);

        assertTrue(forecast.isStable());
        assertEquals(3, forecast.sampleCount());
    }

    @Test
    public void builderRejectsInvalidConfigurationAndReturnRepresentation() {
        Fixture log = fixture(ReturnRepresentation.LOG, 0, 0, 0, 0);
        Fixture decimal = fixture(ReturnRepresentation.DECIMAL, 0, 0, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new RoughVolatilityForecastStateIndicator(decimal.returns()));
        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastStateIndicator.builder(log.returns()).initializationBarCount(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastStateIndicator.builder(log.returns()).decayFactor(1d).build());
        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastStateIndicator.builder(log.returns()).roughnessWindow(2).build());
        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastStateIndicator.builder(log.returns()).volOfVolWindow(1).build());
        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastStateIndicator.builder(log.returns()).horizon(0).build());
        assertThrows(NullPointerException.class,
                () -> RoughVolatilityForecastStateIndicator.builder(log.returns()).driftMode(null).build());
    }

    private RoughVolatilityForecastStateIndicator configured(ReturnIndicator returns, int horizon) {
        return RoughVolatilityForecastStateIndicator.builder(returns)
                .initializationBarCount(2)
                .decayFactor(0.5d)
                .roughnessWindow(4)
                .volOfVolWindow(3)
                .horizon(horizon)
                .build();
    }

    private Fixture fixture(ReturnRepresentation representation, double... values) {
        double[] prices = new double[values.length];
        Arrays.setAll(prices, index -> index + 1d);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        Num[] numbers = Arrays.stream(values)
                .mapToObj(value -> Double.isNaN(value) ? NaN.NaN : numOf(value))
                .toArray(Num[]::new);
        return new Fixture(new FixedReturnIndicator(series, representation, numbers));
    }

    private record Fixture(FixedReturnIndicator returns) {
    }

    private static final class FixedReturnIndicator extends FixedIndicator<Num> implements ReturnIndicator {

        private final ReturnRepresentation representation;

        private FixedReturnIndicator(BarSeries series, ReturnRepresentation representation, Num... values) {
            super(series, values);
            this.representation = representation;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return representation;
        }
    }
}
