/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.analysis.montecarlo.NormalInverseGammaForecastMethod;
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
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
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
    public void rejectsAStateIndicatorThatMisreportsItsReturnStreamRepresentation() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.DECIMAL, numOf(0),
                numOf(1), numOf(2));
        FixedReturnStateIndicator stateIndicator = new FixedReturnStateIndicator(returns, ReturnRepresentation.LOG);

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

    @Test
    public void treatsMissingMomentsAsUnavailable() {
        BarSeries series = constantSeries(6, 100);
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, numOf(0), numOf(0),
                numOf(0), numOf(0), numOf(0), numOf(0));
        FixedMomentStateIndicator state = new FixedMomentStateIndicator(returns, index -> new NullMomentState());
        MonteCarloReturnProjectionIndicator forecast = MonteCarloReturnProjectionIndicator.builder(state)
                .iterationCount(10)
                .lookbackBarCount(2)
                .build();

        assertFalse(forecast.getValue(series.getEndIndex()).isStable());
    }

    @Test
    public void readsCanonicalMomentsInsteadOfOverridableConvenienceAccessors() {
        BarSeries series = constantSeries(6, 100);
        FixedReturnIndicator returns = new FixedReturnIndicator(series, ReturnRepresentation.LOG, numOf(0), numOf(0),
                numOf(0), numOf(0), numOf(0), numOf(0));
        FixedMomentStateIndicator state = new FixedMomentStateIndicator(returns, index -> {
            Num zero = series.numFactory().zero();
            ReturnMoments moments = ReturnMoments.stable(index, index + 1, ReturnRepresentation.LOG, zero, zero, zero);
            return new MisleadingMomentState(moments);
        });
        MonteCarloReturnProjectionIndicator forecast = MonteCarloReturnProjectionIndicator.builder(state)
                .iterationCount(10)
                .lookbackBarCount(2)
                .build();

        assertTrue(forecast.getValue(series.getEndIndex()).isStable());
    }

    @Test
    public void removedIndexRetainsRequestedMetadata() {
        BarSeries series = constantSeries(6, 100);
        MonteCarloReturnProjectionIndicator forecast = forecast(series,
                MonteCarloReturnProjectionIndicator.ShockModel.NORMAL, 2, 10, 2, 7L,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.CONSTANT);

        series.setMaximumBarCount(3);
        Forecast removed = forecast.getValue(1);

        assertEquals(1, removed.decisionIndex());
        assertEquals(2, removed.horizon());
        assertFalse(removed.isStable());
    }

    @Test
    public void preservesLegacySeededShockPathForecasts() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(100, 101, 99, 105, 104, 108, 106, 111)
                .build();

        Forecast empiricalEwma = MonteCarloReturnProjectionIndicator.builder(state(series))
                .horizon(2)
                .iterationCount(200)
                .lookbackBarCount(4)
                .seed(7L)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.STANDARDIZED_EMPIRICAL)
                .volatilityUpdateMode(MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.EWMA)
                .quantiles(0.05, 0.5, 0.95)
                .build()
                .getValue(6);
        assertNumEquals("0.03483255181444142", empiricalEwma.mean());
        assertNumEquals("0.03892637788195455", empiricalEwma.median());
        assertNumEquals("0.04708855100981354", empiricalEwma.standardDeviation());
        assertNumEquals("-0.0369318650958033", empiricalEwma.quantile(0.05));
        assertNumEquals("0.11951942174290628", empiricalEwma.quantile(0.95));

        Forecast normalConstant = MonteCarloReturnProjectionIndicator.builder(state(series))
                .horizon(2)
                .iterationCount(200)
                .lookbackBarCount(4)
                .seed(7L)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.NORMAL)
                .volatilityUpdateMode(MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.CONSTANT)
                .quantiles(0.05, 0.5, 0.95)
                .build()
                .getValue(6);
        assertNumEquals("0.011102619447215032", normalConstant.mean());
        assertNumEquals("0.01397158272752366", normalConstant.median());
        assertNumEquals("0.05563425016603179", normalConstant.standardDeviation());
        assertNumEquals("-0.08046700229217973", normalConstant.quantile(0.05));
        assertNumEquals("0.10387657963270557", normalConstant.quantile(0.95));
        Forecast bootstrapEwma = MonteCarloReturnProjectionIndicator.builder(state(series))
                .horizon(2)
                .iterationCount(200)
                .lookbackBarCount(4)
                .seed(7L)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.HISTORICAL_BOOTSTRAP)
                .volatilityUpdateMode(MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.EWMA)
                .quantiles(0.05, 0.5, 0.95)
                .build()
                .getValue(6);
        assertNumEquals("0.03488116431995178", bootstrapEwma.mean());
        assertNumEquals("0.040148367010780873", bootstrapEwma.median());
        assertNumEquals("0.046971829055689786", bootstrapEwma.standardDeviation());
        assertNumEquals("-0.03738426602430504", bootstrapEwma.quantile(0.05));
        assertNumEquals("0.11768100004586679", bootstrapEwma.quantile(0.95));
    }

    @Test
    public void customMethodOverridesShockConfiguration() {
        BarSeries series = variedSeries();
        MonteCarloReturnProjectionIndicator forecast = MonteCarloReturnProjectionIndicator.builder(state(series))
                .horizon(1)
                .iterationCount(5)
                .lookbackBarCount(2)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.NORMAL)
                .monteCarloMethod(context -> {
                    List<Num> samples = new ArrayList<>();
                    for (int i = 0; i < context.iterationCount(); i++) {
                        samples.add(context.numFactory().numOf(0.25d));
                    }
                    return samples;
                })
                .build();

        Forecast prediction = forecast.getValue(series.getBarCount() - 1);

        assertTrue(prediction.isStable());
        assertEquals(5, prediction.sampleCount());
        assertNumEquals(0.25, prediction.mean());
        assertNumEquals(0.25, prediction.median());
        assertNumEquals(0, prediction.standardDeviation());
    }

    @Test
    public void unstableCustomResultPropagatesAsUnavailableForecast() {
        BarSeries series = variedSeries();
        MonteCarloReturnProjectionIndicator forecast = MonteCarloReturnProjectionIndicator.builder(state(series))
                .lookbackBarCount(2)
                .monteCarloMethod(context -> null)
                .build();

        assertFalse(forecast.getValue(series.getBarCount() - 1).isStable());
    }

    @Test
    public void customResultWithWrongSampleCountIsUnstable() {
        BarSeries series = variedSeries();
        MonteCarloReturnProjectionIndicator forecast = MonteCarloReturnProjectionIndicator.builder(state(series))
                .iterationCount(4)
                .lookbackBarCount(2)
                .monteCarloMethod(context -> List.of(context.numFactory().zero(), context.numFactory().zero()))
                .build();

        assertFalse(forecast.getValue(series.getBarCount() - 1).isStable());
    }

    @Test
    public void undefinedForeignFactorySampleDegradesToUnstable() {
        BarSeries series = variedSeries();
        MonteCarloReturnProjectionIndicator forecast = MonteCarloReturnProjectionIndicator.builder(state(series))
                .iterationCount(4)
                .lookbackBarCount(2)
                .monteCarloMethod(context -> {
                    List<Num> samples = new ArrayList<>();
                    for (int i = 0; i < context.iterationCount(); i++) {
                        samples.add(context.numFactory().zero());
                    }
                    samples.set(0, NaN.NaN);
                    return samples;
                })
                .build();

        assertFalse(forecast.getValue(series.getBarCount() - 1).isStable());
    }

    @Test
    public void nullSampleElementDegradesToUnstable() {
        BarSeries series = variedSeries();
        MonteCarloReturnProjectionIndicator forecast = MonteCarloReturnProjectionIndicator.builder(state(series))
                .iterationCount(4)
                .lookbackBarCount(2)
                .monteCarloMethod(context -> {
                    List<Num> samples = new ArrayList<>();
                    for (int i = 0; i < context.iterationCount(); i++) {
                        samples.add(context.numFactory().zero());
                    }
                    samples.set(0, null);
                    return samples;
                })
                .build();

        assertFalse(forecast.getValue(series.getBarCount() - 1).isStable());
    }

    @Test
    public void foreignFactoryWithOverriddenEqualsCoercesThroughSeriesFactory() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(100, 101, 99, 105, 104, 108, 106, 111)
                .build();
        NumFactory spoofedEqualsFactory = new NumFactory() {
            @Override
            public Num minusOne() {
                return numOf(-1);
            }

            @Override
            public Num zero() {
                return numOf(0);
            }

            @Override
            public Num one() {
                return numOf(1);
            }

            @Override
            public Num two() {
                return numOf(2);
            }

            @Override
            public Num three() {
                return numOf(3);
            }

            @Override
            public Num hundred() {
                return numOf(100);
            }

            @Override
            public Num thousand() {
                return numOf(1000);
            }

            @Override
            public Num numOf(Number number) {
                return DoubleNum.valueOf(number.doubleValue());
            }

            @Override
            public Num numOf(String number) {
                return DoubleNum.valueOf(number);
            }

            @Override
            public boolean equals(Object other) {
                return true;
            }
        };
        MonteCarloReturnProjectionIndicator forecast = MonteCarloReturnProjectionIndicator.builder(state(series))
                .iterationCount(4)
                .lookbackBarCount(2)
                .monteCarloMethod(context -> {
                    List<Num> samples = new ArrayList<>();
                    for (int i = 0; i < context.iterationCount(); i++) {
                        samples.add(spoofedEqualsFactory.numOf(0.1d));
                    }
                    return samples;
                })
                .build();

        Forecast prediction = forecast.getValue(series.getBarCount() - 1);

        assertTrue(prediction.isStable());
        assertEquals("0.1", prediction.mean().toString());
    }

    @Test
    public void smoothedEmpiricalShocksExtendBeyondObservedSupport() {
        BarSeries series = variedSeries();
        Forecast empirical = MonteCarloReturnProjectionIndicator.builder(state(series))
                .horizon(1)
                .iterationCount(500)
                .lookbackBarCount(4)
                .seed(11L)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.STANDARDIZED_EMPIRICAL)
                .volatilityUpdateMode(MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.CONSTANT)
                .build()
                .getValue(series.getBarCount() - 1);
        Forecast smoothed = MonteCarloReturnProjectionIndicator.builder(state(series))
                .horizon(1)
                .iterationCount(500)
                .lookbackBarCount(4)
                .seed(11L)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.SMOOTHED_EMPIRICAL)
                .volatilityUpdateMode(MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.CONSTANT)
                .build()
                .getValue(series.getBarCount() - 1);

        assertTrue(empirical.isStable());
        assertTrue(smoothed.isStable());
        assertTrue(smoothed.standardDeviation().isGreaterThan(empirical.standardDeviation()));
    }

    @Test
    public void smoothedEmpiricalCollapsesOnConstantSeries() {
        BarSeries series = constantSeries(6, 100);
        MonteCarloReturnProjectionIndicator forecast = MonteCarloReturnProjectionIndicator.builder(state(series))
                .iterationCount(50)
                .lookbackBarCount(2)
                .shockModel(MonteCarloReturnProjectionIndicator.ShockModel.SMOOTHED_EMPIRICAL)
                .build();

        Forecast prediction = forecast.getValue(series.getBarCount() - 1);

        assertTrue(prediction.isStable());
        assertNumEquals(0, prediction.median());
        assertNumEquals(0, prediction.standardDeviation());
    }

    @Test
    public void conjugatePosteriorMethodProducesStableForecastThroughBuilder() {
        BarSeries series = variedSeries();
        MonteCarloReturnProjectionIndicator forecast = MonteCarloReturnProjectionIndicator.builder(state(series))
                .horizon(2)
                .iterationCount(100)
                .lookbackBarCount(4)
                .monteCarloMethod(NormalInverseGammaForecastMethod.withEmpiricalPriors())
                .build();

        Forecast prediction = forecast.getValue(series.getBarCount() - 1);

        assertTrue(prediction.isStable());
        assertEquals(100, prediction.sampleCount());
    }

    private EwmaReturnForecastStateIndicator state(BarSeries series) {
        return new EwmaReturnForecastStateIndicator(new LogReturnIndicator(series), 2, 0.5,
                EwmaReturnForecastStateIndicator.DriftMode.ROLLING_MEAN);
    }

    private BarSeries variedSeries() {
        return new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 103, 101, 107, 104, 110, 108, 113)
                .build();
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

    private static final class NullMomentState implements ReturnMomentState {

        @Override
        public ReturnMoments moments() {
            return null;
        }
    }

    private record MisleadingMomentState(ReturnMoments moments) implements ReturnMomentState {

        @Override
        public int index() {
            return moments.index() + 1;
        }

        @Override
        public boolean isStable() {
            return false;
        }

        @Override
        public ReturnRepresentation representation() {
            return ReturnRepresentation.DECIMAL;
        }

        @Override
        public Num mean() {
            return moments.mean().plus(moments.mean().getNumFactory().one());
        }
    }

    private static final class FixedMomentStateIndicator implements ReturnForecastStateIndicator<ReturnMomentState> {

        private final ReturnIndicator returns;
        private final IntFunction<ReturnMomentState> stateFactory;

        private FixedMomentStateIndicator(ReturnIndicator returns, IntFunction<ReturnMomentState> stateFactory) {
            this.returns = returns;
            this.stateFactory = stateFactory;
        }

        @Override
        public ReturnIndicator getReturnIndicator() {
            return returns;
        }

        @Override
        public ReturnMomentState getValue(int index) {
            return stateFactory.apply(index);
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
