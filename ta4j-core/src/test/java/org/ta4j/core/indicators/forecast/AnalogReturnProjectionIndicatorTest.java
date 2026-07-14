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
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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

        assertFalse(inconsistentProjection.getValue(3).isStable());
        assertFalse(nonFiniteProjection.getValue(3).isStable());
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

    private AnalogReturnProjectionIndicator<ReturnForecastState> configured(Fixture fixture, int decisionIndex) {
        return AnalogReturnProjectionIndicator.builder(fixture.states())
                .lookbackBarCount(decisionIndex)
                .neighborCount(2)
                .minimumNeighborCount(2)
                .build();
    }

    private Fixture fixture(double[] returns, double[] stateMeans) {
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
                return values.clone();
            }
        };
    }

    private record Fixture(FixedReturnIndicator returns, FixedStateIndicator states) {
    }

    private static final class FixedReturnIndicator extends AbstractIndicator<Num> implements ReturnIndicator {

        private final double[] values;

        private FixedReturnIndicator(BarSeries series, double[] values) {
            super(series);
            this.values = values.clone();
        }

        @Override
        public Num getValue(int index) {
            return Double.isNaN(values[index]) ? NaN.NaN : getBarSeries().numFactory().numOf(values[index]);
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return ReturnRepresentation.LOG;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
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
