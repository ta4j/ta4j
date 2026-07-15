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
import java.util.Random;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.state.ForecastFeatureExtractors;
import org.ta4j.core.indicators.forecast.state.OnlineChangePointForecastState;
import org.ta4j.core.indicators.forecast.state.RunLengthPosterior;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class OnlineChangePointForecastStateIndicatorTest
        extends AbstractIndicatorTest<OnlineChangePointForecastStateIndicator, OnlineChangePointForecastState> {

    public OnlineChangePointForecastStateIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        double[] prices = new double[40];
        prices[0] = 100d;
        for (int index = 1; index < prices.length; index++) {
            prices[index] = prices[index - 1] * Math.exp(0.01d * Math.sin(index * 0.4d));
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);
        OnlineChangePointForecastStateIndicator indicator = OnlineChangePointForecastStateIndicator.builder(returns)
                .expectedRunLength(80d)
                .maximumRunLength(30)
                .topRunLengthCount(7)
                .minimumObservationCount(8)
                .recentChangeWindow(4)
                .priorMean(0.001d)
                .priorMeanPrecision(0.5d)
                .priorShape(3d)
                .priorScale(0.002d)
                .build();
        return List.of(serializationFixture(series, indicator, 0, 7, 20, 39));
    }

    @Test
    public void constantRegimeProducesNormalizedDeterministicPosterior() {
        double[] values = new double[30];
        Arrays.fill(values, 0.01d);
        Fixture fixture = fixture(ReturnRepresentation.LOG, values);
        OnlineChangePointForecastStateIndicator indicator = configured(fixture.returns(), 20, 21, 5, 5);

        OnlineChangePointForecastState state = indicator.getValue(19);

        assertSame(fixture.returns(), indicator.getReturnIndicator());
        assertEquals(ReturnRepresentation.LOG, indicator.getReturnRepresentation());
        assertEquals(5, indicator.getRecentChangeWindow());
        assertEquals(4, indicator.getCountOfUnstableBars());
        assertTrue(state.isStable());
        assertEquals(20, state.observationCount());
        assertEquals(21, state.topRunLengths().size());
        double posteriorTotal = state.topRunLengths()
                .stream()
                .mapToDouble(posterior -> posterior.probability().doubleValue())
                .sum();
        assertEquals(1d, posteriorTotal, 1e-12);
        RunLengthPosterior zeroRunLength = state.topRunLengths()
                .stream()
                .filter(posterior -> posterior.runLength() == 0)
                .findFirst()
                .orElseThrow();
        assertEquals(0.01d, zeroRunLength.probability().doubleValue(), 1e-12);
        assertTrue(state.recentChangeProbability().doubleValue() < 0.2d);
        assertOrdered(state.topRunLengths());
    }

    @Test
    public void runLengthZeroRetainsPriorWhileGrowthIncludesCurrentObservation() {
        Fixture fixture = fixture(ReturnRepresentation.LOG, 0.25d);
        OnlineChangePointForecastStateIndicator indicator = OnlineChangePointForecastStateIndicator
                .builder(fixture.returns())
                .minimumObservationCount(1)
                .maximumRunLength(4)
                .topRunLengthCount(2)
                .recentChangeWindow(1)
                .build();

        OnlineChangePointForecastState state = indicator.getValue(0);

        assertTrue(state.isStable());
        RunLengthPosterior resetComponent = state.topRunLengths()
                .stream()
                .filter(posterior -> posterior.runLength() == 0)
                .findFirst()
                .orElseThrow();
        RunLengthPosterior growthComponent = state.topRunLengths()
                .stream()
                .filter(posterior -> posterior.runLength() == 1)
                .findFirst()
                .orElseThrow();
        double expectedGrowthMean = 0.25d / 1.0001d;
        double expectedGrowthScale = 1e-4d + 1e-4d / (2d * 1.0001d) * 0.25d * 0.25d;
        assertNumEquals(numFactory.zero(), resetComponent.mean(), 0d);
        assertNumEquals(numFactory.numOf(1e-4d), resetComponent.variance(), 1e-12);
        assertNumEquals(numFactory.numOf(expectedGrowthMean), growthComponent.mean(), 1e-12);
        assertNumEquals(numFactory.numOf(expectedGrowthScale / 1.5d), growthComponent.variance(), 1e-12);
        assertFalse(resetComponent.mean().isEqual(growthComponent.mean()));
    }

    @Test
    public void sharpMeanShiftRaisesRecentChangeProbability() {
        double[] values = new double[80];
        Arrays.fill(values, 0, 40, 0d);
        Arrays.fill(values, 40, values.length, 0.08d);
        Fixture fixture = fixture(ReturnRepresentation.LOG, values);
        OnlineChangePointForecastStateIndicator indicator = OnlineChangePointForecastStateIndicator
                .builder(fixture.returns())
                .expectedRunLength(100d)
                .maximumRunLength(60)
                .topRunLengthCount(8)
                .minimumObservationCount(10)
                .recentChangeWindow(5)
                .priorMeanPrecision(1d)
                .priorShape(2d)
                .priorScale(0.001d)
                .build();

        double beforeShift = indicator.getValue(39).recentChangeProbability().doubleValue();
        double afterShift = indicator.getValue(42).recentChangeProbability().doubleValue();

        assertTrue(afterShift > beforeShift * 2d);
        assertTrue(indicator.getValue(42).mostLikelyRunLength() <= 5);
    }

    @Test
    public void hardTruncationRenormalizesRetainedPosterior() {
        double[] values = new double[30];
        Arrays.setAll(values, index -> 0.02d * Math.sin(index));
        Fixture fixture = fixture(ReturnRepresentation.LOG, values);
        OnlineChangePointForecastStateIndicator indicator = configured(fixture.returns(), 5, 6, 3, 3);

        OnlineChangePointForecastState state = indicator.getValue(29);

        assertTrue(state.isStable());
        assertEquals(6, state.topRunLengths().size());
        assertTrue(state.topRunLengths().stream().allMatch(posterior -> posterior.runLength() <= 5));
        assertEquals(1d, state.topRunLengths().stream().mapToDouble(value -> value.probability().doubleValue()).sum(),
                1e-12);
        assertOrdered(state.topRunLengths());
    }

    @Test
    public void logSpaceFilterSurvivesHighDynamicRangeObservations() {
        double[] values = new double[30];
        Arrays.setAll(values, index -> index % 2 == 0 ? 1e140d : -1e140d);
        Fixture fixture = fixture(ReturnRepresentation.LOG, values);
        OnlineChangePointForecastStateIndicator indicator = configured(fixture.returns(), 20, 5, 5, 3);

        OnlineChangePointForecastState state = indicator.getValue(29);

        assertTrue(state.isStable());
        assertTrue(state.variance().isPositive());
        assertTrue(Num.isFinite(state.recentChangeProbability()));
    }

    @Test
    public void finitePosteriorArithmeticAvoidsIntermediateOverflow() {
        OnlineChangePointForecastState largeScale = OnlineChangePointForecastStateIndicator
                .builder(fixture(ReturnRepresentation.LOG, 0.1d).returns())
                .minimumObservationCount(1)
                .priorMeanPrecision(1d)
                .priorShape(100d)
                .priorScale(1e308d)
                .build()
                .getValue(0);
        OnlineChangePointForecastState largeMean = OnlineChangePointForecastStateIndicator
                .builder(fixture(ReturnRepresentation.LOG, 1e308d).returns())
                .minimumObservationCount(1)
                .priorMean(1e308d)
                .priorMeanPrecision(2d)
                .priorScale(1d)
                .build()
                .getValue(0);
        OnlineChangePointForecastState scaledDifference = OnlineChangePointForecastStateIndicator
                .builder(fixture(ReturnRepresentation.LOG, 1e160d).returns())
                .minimumObservationCount(1)
                .priorMean(-1e160d)
                .priorMeanPrecision(1e-20d)
                .priorScale(1d)
                .build()
                .getValue(0);

        assertTrue(largeScale.isStable());
        assertTrue(Num.isFinite(largeScale.variance()));
        assertTrue(largeMean.isStable());
        assertTrue(Num.isFinite(largeMean.mean()));
        assertTrue(scaledDifference.isStable());
        assertTrue(Num.isFinite(scaledDifference.variance()));
    }

    @Test
    public void posteriorVarianceUnderflowReturnsUnavailableRatherThanZeroDispersion() {
        Fixture fixture = fixture(ReturnRepresentation.LOG, 0d, 0d, 0d, 0d);
        OnlineChangePointForecastStateIndicator indicator = OnlineChangePointForecastStateIndicator
                .builder(fixture.returns())
                .maximumRunLength(4)
                .topRunLengthCount(5)
                .minimumObservationCount(1)
                .recentChangeWindow(1)
                .priorShape(1.1d)
                .priorScale(Double.MIN_VALUE)
                .build();

        assertTrue(indicator.getValue(0).isStable());
        assertFalse(indicator.getValue(3).isStable());
        assertFalse(Num.isFinite(indicator.getValue(3).variance()));
    }

    @Test
    public void underflowedPublishedProbabilityMakesStateUnavailableButTopOnlyRemainsUsable() {
        double[] values = new double[80];
        Arrays.fill(values, 0, 40, -1e140d);
        Arrays.fill(values, 40, values.length, 1e140d);
        Fixture fixture = fixture(ReturnRepresentation.LOG, values);
        OnlineChangePointForecastStateIndicator topOnly = configured(fixture.returns(), 60, 5, 5, 3);
        OnlineChangePointForecastStateIndicator complete = configured(fixture.returns(), 60, 61, 5, 3);

        assertTrue(topOnly.getValue(79).isStable());
        assertFalse(complete.getValue(79).isStable());
    }

    @Test
    public void completePosteriorRemainsAvailableAtSupportedLowDecimalPrecision() {
        NumFactory lowPrecision = DecimalNumFactory.getInstance(3);
        double[] prices = new double[30];
        Arrays.setAll(prices, index -> index + 1d);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(lowPrecision).withData(prices).build();
        Num[] values = new Num[30];
        Arrays.fill(values, lowPrecision.numOf(0.01d));
        ReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, values);
        OnlineChangePointForecastStateIndicator indicator = OnlineChangePointForecastStateIndicator.builder(returns)
                .maximumRunLength(20)
                .topRunLengthCount(21)
                .minimumObservationCount(5)
                .recentChangeWindow(5)
                .build();
        OnlineChangePointForecastStateIndicator partial = OnlineChangePointForecastStateIndicator.builder(returns)
                .maximumRunLength(20)
                .topRunLengthCount(5)
                .minimumObservationCount(5)
                .recentChangeWindow(5)
                .build();

        OnlineChangePointForecastState state = indicator.getValue(19);
        OnlineChangePointForecastState partialState = partial.getValue(19);

        assertTrue(state.isStable());
        assertEquals(21, state.topRunLengths().size());
        assertEquals(1d, state.topRunLengths().stream().mapToDouble(value -> value.probability().doubleValue()).sum(),
                0.01d);
        assertTrue(partialState.isStable());
        assertEquals(5, partialState.topRunLengths().size());
    }

    @Test
    public void variedCompletePosteriorsRemainAvailableAtSupportedLowDecimalPrecision() {
        NumFactory lowPrecision = DecimalNumFactory.getInstance(3);
        Random random = new Random(9137L);
        for (int sample = 0; sample < 200; sample++) {
            double[] prices = new double[30];
            Arrays.setAll(prices, index -> index + 1d);
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(lowPrecision).withData(prices).build();
            Num[] values = new Num[30];
            Arrays.setAll(values, index -> lowPrecision.numOf(random.nextGaussian() * 0.05d));
            ReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, values);
            OnlineChangePointForecastStateIndicator indicator = OnlineChangePointForecastStateIndicator.builder(returns)
                    .maximumRunLength(20)
                    .topRunLengthCount(21)
                    .minimumObservationCount(5)
                    .recentChangeWindow(5)
                    .build();

            assertTrue("sample " + sample, indicator.getValue(19).isStable());
        }
    }

    @Test
    public void invalidInputResetsAndRequiresCompleteWarmupAgain() {
        Fixture fixture = fixture(ReturnRepresentation.LOG, 0.01, 0.02, 0.03, 0.04, 0.05, Double.NaN, 0.06, 0.07, 0.08,
                0.09, 0.10);
        OnlineChangePointForecastStateIndicator indicator = configured(fixture.returns(), 10, 5, 4, 2);

        assertTrue(indicator.getValue(4).isStable());
        assertFalse(indicator.getValue(5).isStable());
        assertEquals(0, indicator.getValue(5).observationCount());
        assertFalse(indicator.getValue(8).isStable());
        assertEquals(3, indicator.getValue(8).observationCount());
        assertTrue(indicator.getValue(9).isStable());
        assertEquals(4, indicator.getValue(9).observationCount());
    }

    @Test
    public void primitiveUnderflowResetsTheValidRun() {
        NumFactory highPrecision = DecimalNumFactory.getInstance(40);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(highPrecision).withData(1, 2, 3, 4, 5).build();
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG,
                highPrecision.numOf(0.01), highPrecision.numOf(0.01), highPrecision.numOf(0.01),
                highPrecision.numOf("1E-10000"), highPrecision.numOf(0.01));
        OnlineChangePointForecastStateIndicator indicator = configured(returns, 10, 5, 3, 2);

        assertEquals(0, indicator.getValue(3).observationCount());
        assertFalse(indicator.getValue(4).isStable());
        assertEquals(1, indicator.getValue(4).observationCount());
    }

    @Test
    public void futureSuffixDoesNotChangeEarlierState() {
        double[] prefix = new double[30];
        Arrays.setAll(prefix, index -> 0.01d * Math.cos(index * 0.3d));
        double[] extended = Arrays.copyOf(prefix, 34);
        Arrays.fill(extended, 30, extended.length, 0.5d);

        OnlineChangePointForecastState prefixState = configured(fixture(ReturnRepresentation.LOG, prefix).returns(), 20,
                5, 5, 3).getValue(29);
        OnlineChangePointForecastState extendedState = configured(fixture(ReturnRepresentation.LOG, extended).returns(),
                20, 5, 5, 3).getValue(29);

        assertEquals(prefixState, extendedState);
    }

    @Test
    public void removedHistoryIsUnavailableAndRetainedHistoryRewarms() {
        double[] prices = new double[15];
        Arrays.setAll(prices, index -> 100d * Math.exp(index * 0.01d));
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        OnlineChangePointForecastStateIndicator indicator = configured(new LogReturnIndicator(series), 10, 5, 4, 2);
        assertTrue(indicator.getValue(14).isStable());

        series.setMaximumBarCount(3);

        OnlineChangePointForecastState removed = indicator.getValue(1);
        OnlineChangePointForecastState retained = indicator.getValue(14);
        assertEquals(1, removed.index());
        assertEquals(0, removed.observationCount());
        assertFalse(removed.isStable());
        assertEquals(2, retained.observationCount());
        assertFalse(retained.isStable());
    }

    @Test
    public void retainedHistoryDoesNotCountReturnWhosePredecessorWasRemoved() {
        double[] prices = new double[15];
        Arrays.setAll(prices, index -> 100d * Math.exp(index * 0.01d));
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        OnlineChangePointForecastStateIndicator indicator = configured(new LogReturnIndicator(series), 10, 5, 3, 2);

        series.setMaximumBarCount(4);

        OnlineChangePointForecastState retained = indicator.getValue(14);
        assertTrue(retained.isStable());
        assertEquals(3, retained.observationCount());
    }

    @Test
    public void retainedHistoryRewarmsTheCompleteReturnLookback() {
        double[] prices = new double[15];
        Arrays.setAll(prices, index -> 100d * Math.exp(index * 0.01d));
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        LogReturnIndicator returns = new LogReturnIndicator(new ClosePriceIndicator(series), 3);
        OnlineChangePointForecastStateIndicator indicator = configured(returns, 10, 5, 3, 2);

        series.setMaximumBarCount(6);

        OnlineChangePointForecastState retained = indicator.getValue(14);
        assertTrue(retained.isStable());
        assertEquals(3, retained.observationCount());
    }

    @Test
    public void specializedSchemaComposesWithAnalogProjection() {
        double[] values = new double[40];
        Arrays.setAll(values, index -> 0.03d * Math.sin(index * 0.7d));
        Fixture fixture = fixture(ReturnRepresentation.LOG, values);
        OnlineChangePointForecastStateIndicator states = configured(fixture.returns(), 20, 5, 3, 2);
        AnalogReturnProjectionIndicator<OnlineChangePointForecastState> projection = AnalogReturnProjectionIndicator
                .builder(states)
                .featureExtractor(ForecastFeatureExtractors.changePoint(states.getRecentChangeWindow()))
                .lookbackBarCount(20)
                .neighborCount(4)
                .minimumNeighborCount(2)
                .build();

        Forecast forecast = projection.getValue(39);

        assertTrue(forecast.isStable());
        assertEquals(4, forecast.sampleCount());
    }

    @Test
    public void builderRejectsInvalidConfigurationAndRepresentation() {
        Fixture log = fixture(ReturnRepresentation.LOG, 0, 0, 0, 0);
        Fixture decimal = fixture(ReturnRepresentation.DECIMAL, 0, 0, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new OnlineChangePointForecastStateIndicator(decimal.returns()));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastStateIndicator.builder(log.returns()).expectedRunLength(1).build());
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastStateIndicator.builder(log.returns()).maximumRunLength(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastStateIndicator.builder(log.returns()).topRunLengthCount(254).build());
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastStateIndicator.builder(log.returns())
                        .minimumObservationCount(0)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastStateIndicator.builder(log.returns()).recentChangeWindow(253).build());
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastStateIndicator.builder(log.returns()).priorMean(Double.NaN).build());
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastStateIndicator.builder(log.returns()).priorMeanPrecision(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastStateIndicator.builder(log.returns()).priorShape(1).build());
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastStateIndicator.builder(log.returns()).priorScale(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastStateIndicator.builder(log.returns())
                        .priorShape(Double.MAX_VALUE)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastStateIndicator.builder(log.returns())
                        .priorShape(10d)
                        .priorScale(Double.MIN_VALUE)
                        .build());

        Num[] values = { numFactory.zero(), numFactory.zero(), numFactory.zero(), numFactory.zero() };
        ReturnIndicator delayed = new FixedReturnIndicator(log.returns().getBarSeries(), ReturnRepresentation.LOG, 2,
                values);
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastStateIndicator.builder(delayed)
                        .minimumObservationCount(Integer.MAX_VALUE)
                        .build());
    }

    private OnlineChangePointForecastStateIndicator configured(ReturnIndicator returns, int maximumRunLength,
            int topRunLengthCount, int minimumObservationCount, int recentChangeWindow) {
        return OnlineChangePointForecastStateIndicator.builder(returns)
                .maximumRunLength(maximumRunLength)
                .topRunLengthCount(topRunLengthCount)
                .minimumObservationCount(minimumObservationCount)
                .recentChangeWindow(recentChangeWindow)
                .build();
    }

    private void assertOrdered(List<RunLengthPosterior> posteriors) {
        for (int i = 1; i < posteriors.size(); i++) {
            RunLengthPosterior previous = posteriors.get(i - 1);
            RunLengthPosterior current = posteriors.get(i);
            assertTrue(previous.probability().isGreaterThan(current.probability())
                    || previous.probability().isEqual(current.probability())
                            && previous.runLength() < current.runLength());
        }
    }

    private Fixture fixture(ReturnRepresentation representation, double... values) {
        double[] prices = new double[values.length];
        Arrays.setAll(prices, index -> index + 1d);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        Num[] numbers = Arrays.stream(values)
                .mapToObj(value -> Double.isNaN(value) ? NaN.NaN : numFactory.numOf(value))
                .toArray(Num[]::new);
        return new Fixture(new FixedReturnIndicator(series, representation, numbers));
    }

    private record Fixture(FixedReturnIndicator returns) {
    }

    private static final class FixedReturnIndicator extends FixedIndicator<Num> implements ReturnIndicator {

        private final ReturnRepresentation representation;
        private final int unstableBarCount;

        private FixedReturnIndicator(BarSeries series, ReturnRepresentation representation, Num... values) {
            this(series, representation, 0, values);
        }

        private FixedReturnIndicator(BarSeries series, ReturnRepresentation representation, int unstableBarCount,
                Num... values) {
            super(series, values);
            this.representation = representation;
            this.unstableBarCount = unstableBarCount;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return representation;
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBarCount;
        }
    }
}
