/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastSupport;
import org.ta4j.core.indicators.forecast.state.ForecastFeatureExtractor;
import org.ta4j.core.indicators.forecast.state.ForecastFeatureSchema;
import org.ta4j.core.indicators.forecast.state.ForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.DecimalNumFactory;

public class AnalogReturnProjectionIndicatorTest
        extends AbstractIndicatorTest<org.ta4j.core.indicators.helpers.LogReturnIndicator, Forecast> {

    public AnalogReturnProjectionIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void constructorDefaultsComposeWithEwmaState() {
        double[] prices = new double[300];
        java.util.Arrays.fill(prices, 100d);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        org.ta4j.core.indicators.helpers.LogReturnIndicator returns = new org.ta4j.core.indicators.helpers.LogReturnIndicator(
                series);
        EwmaReturnForecastStateIndicator states = new EwmaReturnForecastStateIndicator(returns);
        AnalogReturnProjectionIndicator<ReturnForecastState> projection = new AnalogReturnProjectionIndicator<>(states);

        Forecast forecast = projection.getValue(series.getEndIndex());

        assertTrue(forecast.isStable());
        assertEquals(1, projection.getHorizon());
        assertEquals(ReturnRepresentation.LOG, projection.getReturnRepresentation());
        assertEquals(ForecastSupport.empirical(30), forecast.support());
        assertNumEquals(0, forecast.mean());
        assertNumEquals(0, forecast.standardDeviation());
    }

    @Test
    public void explicitReturnBuilderComposesWithCustomRichState() {
        double[] prices = { 100, 100, 100, 100 };
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        FixedReturnIndicator returns = new FixedReturnIndicator(series, new double[] { 0, 5, 7, 0 });
        FixedRegimeStateIndicator states = new FixedRegimeStateIndicator(series, new double[] { 100, 0, 1, 0 });
        AnalogReturnProjectionIndicator<RegimeReturnState> projection = AnalogReturnProjectionIndicator
                .builder(states, returns)
                .lookbackBarCount(3)
                .neighborCount(1)
                .minimumNeighborCount(1)
                .standardizeFeatures(false)
                .build();

        Forecast forecast = projection.getValue(3);

        assertTrue(forecast.isStable());
        assertNumEquals(7, forecast.mean());
        assertEquals(ReturnRepresentation.LOG, projection.getReturnRepresentation());
    }

    @Test
    public void weightedSummaryUsesNormalizedExponentialDistanceWeights() {
        Fixture fixture = fixture(new double[] { 0, 0, 0, 0, 10, 20 }, new double[] { 100, 100, 100, 0, 1, 0 });
        AnalogReturnProjectionIndicator<ReturnForecastState> projection = AnalogReturnProjectionIndicator
                .builder(fixture.states())
                .lookbackBarCount(5)
                .neighborCount(2)
                .minimumNeighborCount(2)
                .standardizeFeatures(false)
                .quantiles(0.05, 0.5, 0.95)
                .build();

        Forecast forecast = projection.getValue(5);

        double distantWeight = Math.exp(-1d);
        double expectedMean = (10d + 20d * distantWeight) / (1d + distantWeight);
        double nearWeight = 1d / (1d + distantWeight);
        double farWeight = 1d - nearWeight;
        double expectedVariance = nearWeight * Math.pow(10d - expectedMean, 2)
                + farWeight * Math.pow(20d - expectedMean, 2);
        assertTrue(forecast.isStable());
        assertEquals(ForecastSupport.empirical(2), forecast.support());
        assertNumEquals(expectedMean, forecast.mean());
        assertNumEquals(Math.sqrt(expectedVariance), forecast.standardDeviation());
        assertNumEquals(10, forecast.median());
        assertNumEquals(10, forecast.quantile(0.05));
        assertNumEquals(20, forecast.quantile(0.95));
    }

    @Test
    public void dispersionRemainsAvailableWhenFiniteReturnSquaresOverflow() {
        Fixture extreme = fixture(new double[] { 0, 1e308, -1e308, 0 }, new double[] { 0, 0, 100, 0 });
        AnalogReturnProjectionIndicator<ReturnForecastState> extremeProjection = AnalogReturnProjectionIndicator
                .builder(extreme.states())
                .lookbackBarCount(3)
                .neighborCount(2)
                .minimumNeighborCount(2)
                .standardizeFeatures(false)
                .build();
        Fixture control = fixture(new double[] { 0, 10, -10, 0 }, new double[] { 0, 0, 100, 0 });
        AnalogReturnProjectionIndicator<ReturnForecastState> controlProjection = AnalogReturnProjectionIndicator
                .builder(control.states())
                .lookbackBarCount(3)
                .neighborCount(2)
                .minimumNeighborCount(2)
                .standardizeFeatures(false)
                .build();

        Forecast extremeForecast = extremeProjection.getValue(3);
        Forecast controlForecast = controlProjection.getValue(3);

        assertTrue(extremeForecast.isStable());
        assertNumEquals(0, extremeForecast.mean());
        assertNumEquals(1e308, extremeForecast.standardDeviation());
        assertTrue(controlForecast.isStable());
        assertNumEquals(10, controlForecast.standardDeviation());
    }

    @Test
    public void tiesUseEarlierCandidateIndex() {
        Fixture fixture = fixture(new double[] { 0, 0, 5, 9 }, new double[] { 100, -1, 1, 0 });
        AnalogReturnProjectionIndicator<ReturnForecastState> projection = AnalogReturnProjectionIndicator
                .builder(fixture.states())
                .lookbackBarCount(2)
                .neighborCount(1)
                .minimumNeighborCount(1)
                .standardizeFeatures(false)
                .build();

        Forecast forecast = projection.getValue(3);

        assertTrue(forecast.isStable());
        assertNumEquals(5, forecast.mean());
        assertEquals(1, forecast.sampleCount());
    }

    @Test
    public void standardizationIsFitOnHistoricalCandidatesOnly() {
        Fixture fixture = fixture(new double[] { 0, 0, 0, 10 }, new double[] { 100, 0, 10, 100 });
        AnalogReturnProjectionIndicator<ReturnForecastState> projection = AnalogReturnProjectionIndicator
                .builder(fixture.states())
                .lookbackBarCount(2)
                .neighborCount(2)
                .minimumNeighborCount(2)
                .build();

        Forecast forecast = projection.getValue(3);

        assertTrue(forecast.isStable());
        assertNumEquals(10d / (1d + Math.exp(-2d)), forecast.mean());
    }

    @Test
    public void disablingStandardizationSkipsIrrelevantTrainingMomentOverflow() {
        Fixture fixture = fixture(new double[] { 0, 1, 2 }, new double[] { -1e154, 1e154, 0 });
        AnalogReturnProjectionIndicator<ReturnForecastState> projection = AnalogReturnProjectionIndicator
                .builder(fixture.states())
                .lookbackBarCount(2)
                .neighborCount(2)
                .minimumNeighborCount(2)
                .standardizeFeatures(false)
                .build();

        Forecast forecast = projection.getValue(2);

        assertTrue(forecast.isStable());
        assertEquals(ForecastSupport.empirical(2), forecast.support());
        assertNumEquals(1.5, forecast.mean());
    }

    @Test
    public void excludesCandidatesWhoseFullHorizonHasNotMatured() {
        Fixture fixture = fixture(new double[] { 0, 0, 0, 3, 4, 92 }, new double[] { 100, 10, 1, 0, 0, 0 });
        AnalogReturnProjectionIndicator<ReturnForecastState> projection = AnalogReturnProjectionIndicator
                .builder(fixture.states())
                .horizon(2)
                .lookbackBarCount(3)
                .neighborCount(1)
                .minimumNeighborCount(1)
                .standardizeFeatures(false)
                .build();

        Forecast forecast = projection.getValue(4);

        assertTrue(forecast.isStable());
        assertNumEquals(7, forecast.mean());
    }

    @Test
    public void decisionForecastIsInvariantToFutureSuffix() {
        Fixture prefix = fixture(new double[] { 0, 1, 2, 3, 4 }, new double[] { 4, 3, 2, 1, 0 });
        Fixture extended = fixture(new double[] { 0, 1, 2, 3, 4, 500, -500 },
                new double[] { 4, 3, 2, 1, 0, -100, 100 });
        AnalogReturnProjectionIndicator<ReturnForecastState> prefixProjection = configured(prefix, 4);
        AnalogReturnProjectionIndicator<ReturnForecastState> extendedProjection = configured(extended, 4);

        Forecast expected = prefixProjection.getValue(4);
        Forecast actual = extendedProjection.getValue(4);

        assertEquals(expected.support(), actual.support());
        assertEquals(expected.mean(), actual.mean());
        assertEquals(expected.quantiles(), actual.quantiles());
    }

    @Test
    public void collapsedNeighborReturnsProduceCoherentZeroDispersion() {
        Fixture fixture = fixture(new double[] { 0, 0, 0, 0, 0 }, new double[] { 4, 3, 2, 1, 0 });
        AnalogReturnProjectionIndicator<ReturnForecastState> projection = configured(fixture, 4);

        Forecast forecast = projection.getValue(4);

        assertTrue(forecast.isStable());
        assertNumEquals(0, forecast.mean());
        assertNumEquals(0, forecast.median());
        assertNumEquals(0, forecast.standardDeviation());
    }

    @Test
    public void collapsedNonzeroReturnsRemainCoherentAtLowDecimalPrecision() {
        Fixture fixture = fixture(DecimalNumFactory.getInstance(3), new double[] { 0.123, 0.123, 0.123, 0.123, 0.123 },
                new double[] { 4, 3, 2, 1, 0 });
        AnalogReturnProjectionIndicator<ReturnForecastState> projection = configured(fixture, 4);

        Forecast forecast = projection.getValue(4);

        assertTrue(forecast.isStable());
        assertNumEquals(0.123, forecast.mean());
        assertNumEquals(0.123, forecast.median());
        assertNumEquals(0, forecast.standardDeviation());
    }

    @Test
    public void collapsedThreeNeighborReturnsRemainExactlyCoherent() {
        Fixture fixture = fixture(new double[] { 0, 5, 5, 5, 0 }, new double[] { 0, 0, 0, 100, 0 });
        AnalogReturnProjectionIndicator<ReturnForecastState> projection = AnalogReturnProjectionIndicator
                .builder(fixture.states())
                .lookbackBarCount(4)
                .neighborCount(3)
                .minimumNeighborCount(3)
                .standardizeFeatures(false)
                .build();

        Forecast forecast = projection.getValue(4);

        assertTrue(forecast.isStable());
        assertEquals(ForecastSupport.empirical(3), forecast.support());
        assertNumEquals(5, forecast.mean());
        assertNumEquals(5, forecast.median());
        assertNumEquals(0, forecast.standardDeviation());
        assertNumEquals(5, forecast.quantile(0.05));
        assertNumEquals(5, forecast.quantile(0.95));
    }

    @Test
    public void lookbackCountsMaturedCandidateRowsForLongHorizons() {
        Fixture fixture = fixture(new double[] { 0, 1, 1, 1, 1, 1 }, new double[] { 0, 0, 0, 0, 0, 0 });
        AnalogReturnProjectionIndicator<ReturnForecastState> projection = AnalogReturnProjectionIndicator
                .builder(fixture.states())
                .horizon(5)
                .lookbackBarCount(3)
                .neighborCount(1)
                .minimumNeighborCount(1)
                .standardizeFeatures(false)
                .build();

        assertFalse(projection.getValue(4).isStable());
        Forecast forecast = projection.getValue(5);
        assertTrue(forecast.isStable());
        assertEquals(ForecastSupport.empirical(1), forecast.support());
        assertNumEquals(5, forecast.mean());
    }

    @Test
    public void invalidSchemaAndSeriesCompositionAreRejected() {
        Fixture fixture = fixture(new double[] { 0, 1, 2 }, new double[] { 0, 1, 2 });
        ForecastFeatureExtractor<ReturnForecastState> decimalExtractor = meanExtractor(ReturnRepresentation.DECIMAL);
        BarSeries otherSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        FixedReturnIndicator otherReturns = new FixedReturnIndicator(otherSeries, new double[] { 0, 1, 2 });

        assertThrows(IllegalArgumentException.class,
                () -> AnalogReturnProjectionIndicator.builder(fixture.states())
                        .featureExtractor(decimalExtractor)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> AnalogReturnProjectionIndicator.builder(fixture.states(), otherReturns).build());
        assertThrows(IllegalArgumentException.class, () -> new AnalogReturnProjectionIndicator<>(fixture.states(), 0));
    }

    @Test
    public void inconsistentOrNonFiniteExtractedFeaturesAreUnavailable() {
        Fixture fixture = fixture(new double[] { 0, 1, 2, 3 }, new double[] { 0, 1, 2, 3 });
        ForecastFeatureExtractor<ReturnForecastState> inconsistent = invalidExtractor(new double[] { 1, 2 });
        ForecastFeatureExtractor<ReturnForecastState> nonFinite = invalidExtractor(
                new double[] { Double.POSITIVE_INFINITY });
        ForecastFeatureExtractor<ReturnForecastState> nullFeatures = invalidExtractor(null);
        AnalogReturnProjectionIndicator<ReturnForecastState> inconsistentProjection = AnalogReturnProjectionIndicator
                .builder(fixture.states())
                .lookbackBarCount(2)
                .neighborCount(1)
                .minimumNeighborCount(1)
                .featureExtractor(inconsistent)
                .build();
        AnalogReturnProjectionIndicator<ReturnForecastState> nonFiniteProjection = AnalogReturnProjectionIndicator
                .builder(fixture.states())
                .lookbackBarCount(2)
                .neighborCount(1)
                .minimumNeighborCount(1)
                .featureExtractor(nonFinite)
                .build();
        AnalogReturnProjectionIndicator<ReturnForecastState> nullProjection = AnalogReturnProjectionIndicator
                .builder(fixture.states())
                .lookbackBarCount(2)
                .neighborCount(1)
                .minimumNeighborCount(1)
                .featureExtractor(nullFeatures)
                .build();

        assertFalse(inconsistentProjection.getValue(3).isStable());
        assertFalse(nonFiniteProjection.getValue(3).isStable());
        assertFalse(nullProjection.getValue(3).isStable());
    }

    @Test
    public void insufficientUsableNeighborsRemainUnavailable() {
        Fixture fixture = fixture(new double[] { 0, 1, Double.NaN, 3 }, new double[] { 0, 1, 2, 3 });
        AnalogReturnProjectionIndicator<ReturnForecastState> projection = AnalogReturnProjectionIndicator
                .builder(fixture.states())
                .lookbackBarCount(3)
                .neighborCount(3)
                .minimumNeighborCount(3)
                .build();

        Forecast forecast = projection.getValue(3);

        assertFalse(forecast.isStable());
        assertEquals(0, forecast.sampleCount());
    }

    @Test
    public void returnWarmupIncludesEveryRequiredMatureNeighbor() {
        double[] returns = new double[16];
        double[] stateMeans = new double[16];
        double[] prices = new double[16];
        java.util.Arrays.fill(prices, 100d);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        FixedReturnIndicator returnIndicator = new FixedReturnIndicator(series, returns, 10);
        FixedStateIndicator states = new FixedStateIndicator(returnIndicator, stateMeans);
        AnalogReturnProjectionIndicator<ReturnForecastState> projection = AnalogReturnProjectionIndicator
                .builder(states)
                .lookbackBarCount(15)
                .neighborCount(5)
                .minimumNeighborCount(5)
                .build();

        assertEquals(14, projection.getCountOfUnstableBars());
        assertFalse(projection.getValue(13).isStable());
        assertTrue(projection.getValue(14).isStable());
    }

    private AnalogReturnProjectionIndicator<ReturnForecastState> configured(Fixture fixture, int decisionIndex) {
        return AnalogReturnProjectionIndicator.builder(fixture.states())
                .lookbackBarCount(decisionIndex)
                .neighborCount(2)
                .minimumNeighborCount(2)
                .build();
    }

    private Fixture fixture(double[] returns, double[] stateMeans) {
        return fixture(numFactory, returns, stateMeans);
    }

    private static Fixture fixture(NumFactory numFactory, double[] returns, double[] stateMeans) {
        double[] prices = new double[returns.length];
        java.util.Arrays.fill(prices, 100d);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        FixedReturnIndicator returnIndicator = new FixedReturnIndicator(series, returns);
        FixedStateIndicator states = new FixedStateIndicator(returnIndicator, stateMeans);
        return new Fixture(returnIndicator, states);
    }

    private static ForecastFeatureExtractor<ReturnForecastState> meanExtractor(ReturnRepresentation representation) {
        ForecastFeatureSchema schema = new ForecastFeatureSchema("test/mean", 1, representation,
                java.util.List.of(new ForecastFeatureSchema.Feature("mean", "return")));
        return new ForecastFeatureExtractor<>() {
            @Override
            public ForecastFeatureSchema schema() {
                return schema;
            }

            @Override
            public void extractInto(ReturnForecastState state, double[] target, int offset) {
                target[offset] = state.mean().doubleValue();
            }
        };
    }

    private static ForecastFeatureExtractor<ReturnForecastState> invalidExtractor(double[] values) {
        ForecastFeatureExtractor<ReturnForecastState> delegate = meanExtractor(ReturnRepresentation.LOG);
        return new ForecastFeatureExtractor<>() {
            @Override
            public ForecastFeatureSchema schema() {
                return delegate.schema();
            }

            @Override
            public void extractInto(ReturnForecastState state, double[] target, int offset) {
                delegate.extractInto(state, target, offset);
            }

            @Override
            public double[] features(ReturnForecastState state) {
                return values == null ? null : values.clone();
            }
        };
    }

    private record Fixture(FixedReturnIndicator returns, FixedStateIndicator states) {
    }

    private record RegimeReturnState(ReturnMoments moments, Num regimeProbability) implements ReturnMomentState {
    }

    private static final class FixedRegimeStateIndicator extends AbstractIndicator<RegimeReturnState>
            implements ForecastStateIndicator<RegimeReturnState> {

        private final double[] means;

        private FixedRegimeStateIndicator(BarSeries series, double[] means) {
            super(series);
            this.means = means.clone();
        }

        @Override
        public RegimeReturnState getValue(int index) {
            NumFactory factory = getBarSeries().numFactory();
            ReturnMoments moments = ReturnMoments.stable(index, index + 1, ReturnRepresentation.LOG,
                    factory.numOf(means[index]), factory.zero(), factory.zero());
            return new RegimeReturnState(moments, factory.one());
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private static final class FixedReturnIndicator extends AbstractIndicator<Num> implements ReturnIndicator {

        private final double[] values;
        private final int unstableBars;

        private FixedReturnIndicator(BarSeries series, double[] values) {
            this(series, values, 0);
        }

        private FixedReturnIndicator(BarSeries series, double[] values, int unstableBars) {
            super(series);
            this.values = values.clone();
            this.unstableBars = unstableBars;
        }

        @Override
        public Num getValue(int index) {
            return index < unstableBars || Double.isNaN(values[index]) ? NaN.NaN
                    : getBarSeries().numFactory().numOf(values[index]);
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return ReturnRepresentation.LOG;
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }
    }

    private static final class FixedStateIndicator extends AbstractIndicator<ReturnForecastState>
            implements ReturnForecastStateIndicator<ReturnForecastState> {

        private final FixedReturnIndicator returns;
        private final double[] means;

        private FixedStateIndicator(FixedReturnIndicator returns, double[] means) {
            super(returns.getBarSeries());
            this.returns = returns;
            this.means = means.clone();
        }

        @Override
        public ReturnForecastState getValue(int index) {
            NumFactory factory = getBarSeries().numFactory();
            return ReturnForecastState.stable(index, index + 1, ReturnRepresentation.LOG, factory.numOf(means[index]),
                    factory.zero(), factory.zero());
        }

        @Override
        public FixedReturnIndicator getReturnIndicator() {
            return returns;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }
}
