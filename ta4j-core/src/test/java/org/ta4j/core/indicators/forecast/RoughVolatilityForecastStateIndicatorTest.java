/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RoughVolatilityForecastStateIndicatorTest
        extends AbstractIndicatorTest<RoughVolatilityForecastStateIndicator, RoughVolatilityForecastState> {

    public RoughVolatilityForecastStateIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        double[] prices = new double[20];
        Arrays.setAll(prices, index -> Math.exp(0.01d * index));
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        RoughVolatilityForecastStateIndicator indicator = RoughVolatilityForecastStateIndicator
                .builder(new LogReturnIndicator(series))
                .initializationBarCount(4)
                .decayFactor(0.8d)
                .driftMode(EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN)
                .roughnessWindow(6)
                .volOfVolWindow(4)
                .horizon(3)
                .build();
        return List.of(serializationFixture(series, indicator, 0, 6, 19));
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
    public void constantReturnsRemainStableWithLowPrecisionProxyArithmetic() {
        NumFactory lowPrecision = DecimalNumFactory.getInstance(2);
        Fixture fixture = fixture(lowPrecision, ReturnRepresentation.LOG, 0, 0, 0, 0, 0, 0, 0, 0);
        RoughVolatilityForecastStateIndicator indicator = configured(fixture.returns(), 3);

        RoughVolatilityForecastState state = indicator.getValue(7);

        assertTrue(state.isStable());
        assertNumEquals(0.01d, state.roughnessHurst());
        assertNumEquals(0d, state.volOfVol());
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
    public void unsupportedLowPrecisionLogReturnsUnavailableStateInsteadOfThrowing() {
        NumFactory lowPrecision = DecimalNumFactory.getInstance(1);
        double[] returns = new double[8];
        Arrays.setAll(returns, index -> Math.exp(-5d + 0.2d * index) - 1e-8d);
        Fixture fixture = fixture(lowPrecision, ReturnRepresentation.LOG, returns);
        RoughVolatilityForecastStateIndicator indicator = RoughVolatilityForecastStateIndicator
                .builder(fixture.returns())
                .initializationBarCount(2)
                .decayFactor(0.5d)
                .roughnessWindow(6)
                .volOfVolWindow(4)
                .horizon(3)
                .build();

        RoughVolatilityForecastState state = indicator.getValue(7);

        assertEquals(7, state.index());
        assertEquals(ReturnRepresentation.LOG, state.representation());
        assertFalse(state.isStable());
        assertTrue(state.roughnessHurst().isNaN());
        assertTrue(state.volOfVol().isNaN());
        assertTrue(state.horizonVarianceForecasts().isEmpty());
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
    public void horizonTermStructureUsesThePublishedFactoryNormalizedHurst() {
        NumFactory lowPrecision = DecimalNumFactory.getInstance(2);
        double[] values = { 0.006861265232875077, 0.007339558476995448, 0.0069607957350965805, 0.007663456496143692,
                0.008237036502068093, 0.007956710727302023, 0.008252665826692583, 0.008304753891375239,
                0.00784609879723065, 0.007249432430059512, 0.006652761799220903, 0.006416085270374187 };
        Fixture fixture = fixture(lowPrecision, ReturnRepresentation.LOG, values);
        RoughVolatilityForecastState state = RoughVolatilityForecastStateIndicator.builder(fixture.returns())
                .initializationBarCount(2)
                .decayFactor(0.5d)
                .roughnessWindow(12)
                .volOfVolWindow(3)
                .horizon(1_000)
                .build()
                .getValue(11);

        Num expectedFactor = lowPrecision.numOf(Math.pow(1_000d, 2d * state.roughnessHurst().doubleValue()));
        assertTrue(state.isStable());
        assertNumEquals(state.variance().multipliedBy(expectedFactor), state.horizonVarianceForecasts().get(999));
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
    public void removedIndexRetainsRequestedStateMetadata() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 101, 102, 103, 104, 105, 106, 107)
                .build();
        RoughVolatilityForecastStateIndicator indicator = configured(new LogReturnIndicator(series), 2);

        series.setMaximumBarCount(3);
        RoughVolatilityForecastState removed = indicator.getValue(1);

        assertEquals(1, removed.index());
        assertEquals(0, removed.observationCount());
        assertFalse(removed.isStable());
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
        return fixture(numFactory, representation, values);
    }

    private Fixture fixture(NumFactory factory, ReturnRepresentation representation, double... values) {
        double[] prices = new double[values.length];
        Arrays.setAll(prices, index -> index + 1d);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(prices).build();
        Num[] numbers = Arrays.stream(values)
                .mapToObj(value -> Double.isNaN(value) ? NaN.NaN : factory.numOf(value))
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
