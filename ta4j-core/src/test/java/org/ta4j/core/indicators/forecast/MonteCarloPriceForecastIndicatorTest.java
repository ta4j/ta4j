/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertNotSame;

import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastSupport;
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.DoubleNumFactory;

public class MonteCarloPriceForecastIndicatorTest
        extends AbstractIndicatorTest<MonteCarloPriceForecastIndicator, Forecast> {

    public MonteCarloPriceForecastIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void horizonConstructorInfersPriceSourceFromLogReturns() {
        BarSeries series = constantSeries(300, 100);
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns);
        MonteCarloPriceForecastIndicator forecast = new MonteCarloPriceForecastIndicator(state, 5);

        Forecast prediction = forecast.getValue(series.getEndIndex());

        assertTrue(prediction.isStable());
        assertEquals(5, forecast.getHorizon());
        assertEquals(ForecastSupport.empirical(1_000), prediction.support());
        assertNumEquals(100, prediction.mean());
        assertNumEquals(0, prediction.standardDeviation());
    }

    @Test
    public void summarizesTransformedNonnormalPathsExactly() {
        Forecast prediction = explicitHistoricalForecast(Math.log(0.9), Math.log(1.1));

        assertNumEquals(100, prediction.mean());
        assertNumEquals(100, prediction.median());
        assertNumEquals(10d, prediction.standardDeviation());
        assertNumEquals(90d, prediction.quantile(0.0));
        assertNumEquals(110d, prediction.quantile(1.0));
    }

    @Test
    public void stronglyNonnormalPathsMatchDirectPriceSampleSummary() {
        Forecast prediction = explicitHistoricalForecast(-1, 1);
        Num price = numOf(100);
        Forecast expected = Forecast.ofSamples(2, 1,
                List.of(price.multipliedBy(numOf(-1).exp()), price.multipliedBy(numOf(1).exp())),
                List.of(0.0, 0.5, 1.0));

        assertEquivalent(expected, prediction);
    }

    @Test
    public void inferredAndExplicitPriceSourcesUseTheSameSimulation() {
        BarSeries series = constantSeries(300, 100);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns);
        MonteCarloPriceForecastIndicator inferred = new MonteCarloPriceForecastIndicator(state, 5);
        MonteCarloPriceForecastIndicator explicit = new MonteCarloPriceForecastIndicator(close, state, 5);

        assertEquivalent(inferred.getValue(series.getEndIndex()), explicit.getValue(series.getEndIndex()));
    }

    @Test
    public void rejectsCustomSourceInferenceButSupportsExplicitPrice() {
        BarSeries series = constantSeries(3, 100);
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, numOf(0), numOf(0),
                numOf(0));
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG);

        assertThrows(IllegalArgumentException.class, () -> new MonteCarloPriceForecastIndicator(state, 1));
        MonteCarloPriceForecastIndicator explicit = MonteCarloPriceForecastIndicator
                .builder(new ClosePriceIndicator(series), state)
                .lookbackBarCount(2)
                .iterationCount(2)
                .build();
        assertTrue(explicit.getValue(2).isStable());
    }

    @Test
    public void rejectsNonLogStateIndicator() {
        BarSeries series = constantSeries(3, 100);
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.DECIMAL, numOf(0),
                numOf(0), numOf(0));
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.DECIMAL);

        assertThrows(IllegalArgumentException.class,
                () -> new MonteCarloPriceForecastIndicator(new ClosePriceIndicator(series), state, 1));
    }

    @Test
    public void rejectsNonzeroTerminalPricesThatUnderflowToZero() {
        NumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(doubleFactory)
                .withData(1e-300, 1e-300, 1e-300)
                .build();
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, doubleFactory.zero(),
                doubleFactory.numOf(-100), doubleFactory.numOf(-100));
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG);
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator
                .builder(new ClosePriceIndicator(series), state)
                .horizon(1)
                .iterationCount(1)
                .lookbackBarCount(2)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP)
                .build();

        assertFalse(forecast.getValue(2).isStable());
    }

    @Test
    public void removedIndexRetainsRequestedMetadata() {
        BarSeries series = constantSeries(6, 100);
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ZERO);
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator.builder(state)
                .horizon(2)
                .iterationCount(10)
                .lookbackBarCount(2)
                .build();

        series.setMaximumBarCount(3);
        Forecast removed = forecast.getValue(1);

        assertEquals(1, removed.decisionIndex());
        assertEquals(2, removed.horizon());
        assertFalse(removed.isStable());
    }

    @Test
    public void restartingStateDiscardsCachedForecastsAfterHeadAdvance() {
        double[] prices = new double[600];
        java.util.Arrays.fill(prices, 100d);
        BaseBarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(new LogReturnIndicator(series));
        CountingRestartStateIndicator countingState = new CountingRestartStateIndicator(state);
        MonteCarloPriceForecastIndicator forecast = new MonteCarloPriceForecastIndicator(countingState, 1);

        Forecast initial = forecast.getValue(series.getEndIndex());
        assertTrue(initial.isStable());
        long readsBeforeAdvance = countingState.reads();

        series.setMaximumBarCount(300);
        Forecast afterAdvance = forecast.getValue(series.getEndIndex());

        assertNotSame(initial, afterAdvance);
        assertTrue(afterAdvance.isStable());
        assertTrue(countingState.reads() > readsBeforeAdvance);
    }

    @Test
    public void stateRestartContractFlagsOnlineChangePointAndNotEwma() {
        BarSeries series = constantSeries(300, 100);
        LogReturnIndicator returns = new LogReturnIndicator(series);

        assertFalse(new EwmaReturnForecastStateIndicator(returns).restartsAfterHeadAdvance());
        assertTrue(OnlineChangePointForecastStateIndicator.builder(returns).build().restartsAfterHeadAdvance());
    }

    private static final class CountingRestartStateIndicator
            implements ReturnForecastStateIndicator<ReturnForecastState> {

        private final ReturnForecastStateIndicator<ReturnForecastState> delegate;
        private final AtomicInteger reads = new AtomicInteger();

        private CountingRestartStateIndicator(ReturnForecastStateIndicator<ReturnForecastState> delegate) {
            this.delegate = delegate;
        }

        @Override
        public ReturnForecastState getValue(int index) {
            reads.incrementAndGet();
            return delegate.getValue(index);
        }

        @Override
        public int getCountOfUnstableBars() {
            return delegate.getCountOfUnstableBars();
        }

        @Override
        public BarSeries getBarSeries() {
            return delegate.getBarSeries();
        }

        @Override
        public ReturnIndicator getReturnIndicator() {
            return delegate.getReturnIndicator();
        }

        @Override
        public ReturnRepresentation getReturnRepresentation() {
            return delegate.getReturnRepresentation();
        }

        @Override
        public boolean restartsAfterHeadAdvance() {
            return true;
        }

        long reads() {
            return reads.get();
        }
    }

    @Test
    public void customMethodReplacesShockSimulation() {
        BarSeries series = constantSeries(6, 100);
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ZERO);
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator
                .builder(new ClosePriceIndicator(series), state)
                .horizon(1)
                .iterationCount(25)
                .lookbackBarCount(2)
                .monteCarloMethod(context -> {
                    List<Num> samples = new ArrayList<>();
                    for (int i = 0; i < context.iterationCount(); i++) {
                        samples.add(context.numFactory().zero());
                    }
                    return samples;
                })
                .build();

        Forecast prediction = forecast.getValue(series.getBarCount() - 1);

        assertTrue(prediction.isStable());
        assertEquals(25, prediction.sampleCount());
        assertNumEquals(numOf(100), prediction.mean());
        assertNumEquals(numOf(100), prediction.median());
        assertNumEquals(0, prediction.standardDeviation());
    }

    private Forecast explicitHistoricalForecast(double down, double up) {
        BarSeries series = constantSeries(3, 100);
        Indicator<Num> close = new ClosePriceIndicator(series);
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, numOf(0), numOf(down),
                numOf(up));
        FixedReturnStateIndicator state = new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG);
        return MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(1)
                .iterationCount(2)
                .lookbackBarCount(2)
                .seed(3L)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP)
                .quantiles(0.0, 0.5, 1.0)
                .build()
                .getValue(2);
    }

    private BarSeries constantSeries(int barCount, double value) {
        double[] values = new double[barCount];
        Arrays.fill(values, value);
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(values).build();
    }

    private void assertEquivalent(Forecast expected, Forecast actual) {
        assertEquals(expected.support(), actual.support());
        assertEquals(expected.horizon(), actual.horizon());
        assertNumEquals(expected.mean(), actual.mean());
        assertNumEquals(expected.median(), actual.median());
        assertNumEquals(expected.standardDeviation(), actual.standardDeviation());
        for (Double probability : List.of(0.0, 0.5, 1.0)) {
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
}
