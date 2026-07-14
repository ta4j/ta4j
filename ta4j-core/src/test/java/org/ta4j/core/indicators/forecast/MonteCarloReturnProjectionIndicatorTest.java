/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MonteCarloReturnProjectionIndicatorTest extends AbstractIndicatorTest<LogReturnIndicator, Forecast> {

    public MonteCarloReturnProjectionIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void horizonConstructorBuildsUsableDefaultStateForecast() {
        BarSeries series = constantSeries(300, 100);
        LogReturnIndicator returns = new LogReturnIndicator(series);
        ReturnForecastStateIndicator<ReturnForecastState> state = new EwmaReturnForecastStateIndicator(returns);
        MonteCarloReturnProjectionIndicator forecast = new MonteCarloReturnProjectionIndicator(state, 5);

        Forecast prediction = forecast.getValue(series.getEndIndex());

        assertEquals(ReturnRepresentation.LOG, forecast.getReturnRepresentation());
        assertTrue(prediction.isStable());
        assertEquals(5, prediction.horizon());
        assertNumEquals(0, prediction.median());
        assertNumEquals(0, prediction.standardDeviation());
    }

    @Test
    public void constantPriceProducesCollapsedReturnForecast() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 100)
                .build();
        MonteCarloReturnProjectionIndicator forecast = forecast(series,
                MonteCarloReturnProjectionIndicator.ShockModel.STANDARDIZED_EMPIRICAL, 2, 50, 3, 42L,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.CONSTANT);

        Forecast prediction = forecast.getValue(3);

        assertTrue(prediction.isStable());
        assertEquals(50, prediction.sampleCount());
        assertNumEquals(0, prediction.mean());
        assertNumEquals(0, prediction.median());
        assertNumEquals(0, prediction.standardDeviation());
    }

    @Test
    public void sameSeedAndIndexAreIndependentOfCallOrder() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 101, 99, 105, 104, 108, 106, 111)
                .build();
        MonteCarloReturnProjectionIndicator first = forecast(series,
                MonteCarloReturnProjectionIndicator.ShockModel.NORMAL, 2, 100, 4, 7L,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.EWMA);
        MonteCarloReturnProjectionIndicator second = forecast(series,
                MonteCarloReturnProjectionIndicator.ShockModel.NORMAL, 2, 100, 4, 7L,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.EWMA);

        Forecast expected = first.getValue(6);
        second.getValue(7);
        Forecast actual = second.getValue(6);

        assertEquivalent(expected, actual);
    }

    @Test
    public void forecastAtDecisionIndexIsInvariantToFutureSeriesSuffix() {
        BarSeries prefix = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 101, 99, 105, 104, 108, 106)
                .build();
        BarSeries extended = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 101, 99, 105, 104, 108, 106, 500, 10)
                .build();
        MonteCarloReturnProjectionIndicator prefixForecast = forecast(prefix,
                MonteCarloReturnProjectionIndicator.ShockModel.STANDARDIZED_EMPIRICAL, 3, 100, 4, 7L,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.EWMA);
        MonteCarloReturnProjectionIndicator extendedForecast = forecast(extended,
                MonteCarloReturnProjectionIndicator.ShockModel.STANDARDIZED_EMPIRICAL, 3, 100, 4, 7L,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.EWMA);

        assertEquivalent(prefixForecast.getValue(6), extendedForecast.getValue(6));
    }

    @Test
    public void empiricalAndNormalShockModelsProduceStableOrderedQuantiles() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 103, 101, 107, 104, 110, 108, 113)
                .build();
        MonteCarloReturnProjectionIndicator empirical = forecast(series,
                MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP, 1, 100, 4, 11L,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.CONSTANT);
        MonteCarloReturnProjectionIndicator normal = forecast(series,
                MonteCarloReturnProjectionIndicator.ShockModel.NORMAL, 1, 100, 4, 11L,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.CONSTANT);

        Forecast empiricalPrediction = empirical.getValue(6);
        Forecast normalPrediction = normal.getValue(6);

        assertTrue(empiricalPrediction.isStable());
        assertTrue(normalPrediction.isStable());
        assertTrue(empiricalPrediction.quantile(0.05).isLessThanOrEqual(empiricalPrediction.quantile(0.95)));
        assertTrue(normalPrediction.standardDeviation().isPositive());
    }

    @Test
    public void rejectsNonLogStateIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.DECIMAL, numOf(0),
                numOf(1), numOf(2));
        FixedReturnStateIndicator stateIndicator = new FixedReturnStateIndicator(returns, ReturnRepresentation.DECIMAL);

        assertThrows(IllegalArgumentException.class, () -> new MonteCarloReturnProjectionIndicator(stateIndicator));
    }

    @Test
    public void acceptsCustomReturnDerivedForecastState() {
        BarSeries series = constantSeries(6, 100);
        LogReturnIndicator returns = new LogReturnIndicator(series);
        ReturnForecastStateIndicator<CustomReturnState> state = new FixedCustomStateIndicator(returns);
        MonteCarloReturnProjectionIndicator forecast = MonteCarloReturnProjectionIndicator.builder(state)
                .iterationCount(10)
                .lookbackBarCount(2)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.NORMAL)
                .build();

        Forecast prediction = forecast.getValue(series.getEndIndex());

        assertTrue(prediction.isStable());
        assertEquals(10, prediction.sampleCount());
    }

    @Test
    public void acceptsCustomStateUsingADifferentNumFactory() {
        BarSeries series = constantSeries(6, 100);
        LogReturnIndicator returns = new LogReturnIndicator(series);
        ReturnForecastStateIndicator<CustomReturnState> state = new FixedCustomStateIndicator(returns,
                DecimalNumFactory.getInstance());
        MonteCarloReturnProjectionIndicator forecast = MonteCarloReturnProjectionIndicator.builder(state)
                .iterationCount(10)
                .lookbackBarCount(2)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.NORMAL)
                .build();

        Forecast prediction = forecast.getValue(series.getEndIndex());

        assertTrue(prediction.isStable());
        assertEquals(10, prediction.sampleCount());
        assertEquals(series.numFactory().one().getClass(), prediction.mean().getClass());
        if (prediction.mean() instanceof DecimalNum actual
                && series.numFactory().one() instanceof DecimalNum expected) {
            assertEquals(expected.getMathContext(), actual.getMathContext());
        }
    }

    @Test
    public void rejectsCustomStateWithInvalidStableMetadata() {
        BarSeries series = constantSeries(6, 100);
        LogReturnIndicator returns = new LogReturnIndicator(series);
        ReturnForecastStateIndicator<CustomReturnState> futureState = new FixedCustomStateIndicator(returns,
                series.numFactory(), 1, 1);
        ReturnForecastStateIndicator<CustomReturnState> emptyState = new FixedCustomStateIndicator(returns,
                series.numFactory(), 0, 0);
        MonteCarloReturnProjectionIndicator futureStateForecast = MonteCarloReturnProjectionIndicator
                .builder(futureState)
                .iterationCount(10)
                .lookbackBarCount(2)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.NORMAL)
                .build();
        MonteCarloReturnProjectionIndicator emptyStateForecast = MonteCarloReturnProjectionIndicator.builder(emptyState)
                .iterationCount(10)
                .lookbackBarCount(2)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.NORMAL)
                .build();

        assertFalse(futureStateForecast.getValue(series.getEndIndex()).isStable());
        assertFalse(emptyStateForecast.getValue(series.getEndIndex()).isStable());
    }

    @Test
    public void rejectsStateMomentsWhoseRepresentationDiffersFromTheReturnStream() {
        BarSeries series = constantSeries(6, 100);
        LogReturnIndicator returns = new LogReturnIndicator(series);
        ReturnForecastStateIndicator<CustomReturnState> state = new FixedCustomStateIndicator(returns,
                series.numFactory(), 0, -1, ReturnRepresentation.DECIMAL);
        MonteCarloReturnProjectionIndicator forecast = MonteCarloReturnProjectionIndicator.builder(state)
                .iterationCount(10)
                .lookbackBarCount(2)
                .build();

        assertFalse(forecast.getValue(series.getEndIndex()).isStable());
    }

    private MonteCarloReturnProjectionIndicator forecast(BarSeries series,
            MonteCarloReturnProjectionIndicator.ShockModel shockModel, int horizon, int iterations, int lookback,
            long seed, MonteCarloReturnProjectionIndicator.VolatilityUpdateMode updateMode) {
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);
        return MonteCarloReturnProjectionIndicator.builder(state)
                .horizon(horizon)
                .iterationCount(iterations)
                .lookbackBarCount(lookback)
                .seed(seed)
                .shockModel(shockModel)
                .volatilityUpdateMode(updateMode)
                .quantiles(0.05, 0.5, 0.95)
                .build();
    }

    private BarSeries constantSeries(int barCount, double value) {
        double[] values = new double[barCount];
        Arrays.fill(values, value);
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(values).build();
    }

    private void assertEquivalent(Forecast expected, Forecast actual) {
        assertEquals(expected.isStable(), actual.isStable());
        assertEquals(expected.sampleCount(), actual.sampleCount());
        assertNumEquals(expected.mean(), actual.mean());
        assertNumEquals(expected.median(), actual.median());
        assertNumEquals(expected.standardDeviation(), actual.standardDeviation());
        for (Double probability : List.of(0.05, 0.5, 0.95)) {
            assertNumEquals(expected.quantile(probability), actual.quantile(probability));
        }
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

    private static final class FixedReturnStateIndicator implements ReturnForecastStateIndicator<ReturnForecastState> {

        private final ReturnIndicator returns;
        private final ReturnRepresentation representation;

        private FixedReturnStateIndicator(ReturnIndicator returns, ReturnRepresentation representation) {
            this.returns = returns;
            this.representation = representation;
        }

        @Override
        public ReturnIndicator getReturnIndicator() {
            return returns;
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return representation;
        }

        @Override
        public ReturnForecastState getValue(int index) {
            Num zero = getBarSeries().numFactory().zero();
            return ReturnForecastState.stable(index, index + 1, representation, zero, zero, zero);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return returns.getBarSeries();
        }
    }

    private record CustomReturnState(ReturnMoments moments) implements ReturnMomentState {
    }

    private static final class FixedCustomStateIndicator implements ReturnForecastStateIndicator<CustomReturnState> {

        private final ReturnIndicator returns;
        private final NumFactory stateNumFactory;
        private final int stateIndexOffset;
        private final int observationCount;
        private final ReturnRepresentation momentRepresentation;

        private FixedCustomStateIndicator(ReturnIndicator returns) {
            this(returns, returns.getBarSeries().numFactory(), 0, -1);
        }

        private FixedCustomStateIndicator(ReturnIndicator returns, NumFactory stateNumFactory) {
            this(returns, stateNumFactory, 0, -1);
        }

        private FixedCustomStateIndicator(ReturnIndicator returns, NumFactory stateNumFactory, int stateIndexOffset,
                int observationCount) {
            this(returns, stateNumFactory, stateIndexOffset, observationCount, ReturnRepresentation.LOG);
        }

        private FixedCustomStateIndicator(ReturnIndicator returns, NumFactory stateNumFactory, int stateIndexOffset,
                int observationCount, ReturnRepresentation momentRepresentation) {
            this.returns = returns;
            this.stateNumFactory = stateNumFactory;
            this.stateIndexOffset = stateIndexOffset;
            this.observationCount = observationCount;
            this.momentRepresentation = momentRepresentation;
        }

        @Override
        public ReturnIndicator getReturnIndicator() {
            return returns;
        }

        @Override
        public CustomReturnState getValue(int index) {
            Num zero = stateNumFactory.zero();
            int representedObservations = observationCount < 0 ? index + 1 : observationCount;
            ReturnMoments moments = representedObservations == 0
                    ? ReturnMoments.unstable(index + stateIndexOffset, 0, momentRepresentation)
                    : ReturnMoments.stable(index + stateIndexOffset, representedObservations, momentRepresentation,
                            zero, zero, zero);
            return new CustomReturnState(moments);
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return returns.getBarSeries();
        }
    }
}
