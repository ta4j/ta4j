/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

public class ProcessCapabilityPositionSizerTest {

    private static final NumFactory DECIMAL_NUM_FACTORY = DecimalNumFactory.getInstance();

    private NumFactory numFactory = DoubleNumFactory.getInstance();

    private Num numOf(Number value) {
        return numFactory.numOf(value);
    }

    private void runWithNumFactory(NumFactory factory, Runnable test) {
        NumFactory previousFactory = numFactory;
        numFactory = factory;
        try {
            test.run();
        } finally {
            numFactory = previousFactory;
        }
    }

    @Test
    public void usesEntryIndexForSizing() {
        runWithNumFactory(DECIMAL_NUM_FACTORY, this::assertUsesEntryIndexForSizing);
        assertUsesEntryIndexForSizing();
    }

    private void assertUsesEntryIndexForSizing() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(series, numOf(0), numOf(5), numOf(15));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, 100, 10);

        // amount = baseAmount / (1 + max(0, statistic) / controlLimit), evaluated
        // at the entry index rather than the signal index.
        assertNumEquals(100, sizer.amount(context(series, 0, 0)));
        assertNumEquals(200.0 / 3.0, sizer.amount(context(series, 1, 1)));
        assertNumEquals(40, sizer.amount(context(series, 0, 2)));
    }

    @Test
    public void failsOpenOnNonFiniteStatistic() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(series, numOf(0), NaN.NaN, numOf(15));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, 100, 10);

        assertNumEquals(100, sizer.amount(context(series, 1, 1)));
    }

    @Test
    public void underflowReturnsEpsilonAmount() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(series, numOf(0), numOf(Double.MAX_VALUE));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, 100, Double.MIN_NORMAL);

        // DoubleNum: statistic / controlLimit overflows to infinity, collapsing
        // the damped amount to zero; the sizer floors it at the factory epsilon
        // instead of failing the backtest with a non-positive amount.
        assertNumEquals(numFactory.epsilon(), sizer.amount(context(series, 1, 1)));
    }

    @Test
    public void underflowFloorDoesNotExceedBaseAmount() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(series, numOf(0), numOf(Double.MAX_VALUE));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, Double.MIN_NORMAL, Double.MIN_NORMAL);

        // The damped amount underflows to zero, but the epsilon floor must
        // never exceed the configured base amount.
        assertNumEquals(Double.MIN_NORMAL, sizer.amount(context(series, 1, 1)));
    }

    @Test
    public void acceptsControlLimitBeyondDoubleRangeForDecimalNum() {
        runWithNumFactory(DECIMAL_NUM_FACTORY, () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
            FixedIndicator<Num> statistic = new FixedIndicator<>(series, DECIMAL_NUM_FACTORY.numOf(0),
                    DECIMAL_NUM_FACTORY.numOf(5));
            PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, 100, new BigDecimal("1e400"));

            // DecimalNum carries 1e400 exactly; the denominator 1 + 5/1e400
            // rounds back to 1, leaving the full base amount.
            assertNumEquals(100, sizer.amount(context(series, 1, 1)));
        });
    }

    @Test
    public void coercesStatisticIntoContextFactory() {
        // A DecimalNum capability statistic consumed in a DoubleNum backtest
        // context must be coerced instead of mixing factories.
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> decimalStatistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(5));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(decimalStatistic, 100, 10);

        runWithNumFactory(DoubleNumFactory.getInstance(), () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            // 100 / (1 + 5 / 10) = 200 / 3.
            assertNumEquals(200.0 / 3.0, sizer.amount(context(series, 1, 1)));
        });
    }

    @Test
    public void coercesValuesIntoExactContextFactoryConfiguration() {
        // NumFactory.produces() matches on the implementation class, so a
        // DecimalNum statistic and base amount pass the guard even when the
        // backtest context uses a DecimalNumFactory with a different
        // precision. Both values must round through the context factory's
        // exact configuration instead of leaking the indicator's precision
        // into the sizing arithmetic.
        NumFactory precisionTenFactory = DecimalNumFactory.getInstance(10);
        NumFactory precisionOneFactory = DecimalNumFactory.getInstance(1);
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(precisionTenFactory).withData(1, 2).build();
        FixedIndicator<Num> decimalStatistic = new FixedIndicator<>(decimalSeries, precisionTenFactory.numOf(0),
                precisionTenFactory.numOf(new BigDecimal("0.3333333333")));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(decimalStatistic, new BigDecimal("0.16"), 1);

        runWithNumFactory(precisionOneFactory, () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            // Precision 1 rounds the statistic ratio 1/3 to 0.3, the base
            // amount 0.16 to 0.2, and the denominator 1 + 0.3 back to 1:
            // amount = 0.2 * 1 / 1 = 0.2. The indicator's precision-10
            // arithmetic would instead give 0.16 * 1 / 1.333333333 = 0.12.
            assertNumEquals(0.2, sizer.amount(context(series, 1, 1)));
        });
    }

    @Test
    public void positiveOverflowCoercionSaturatesToEpsilonFloor() {
        // A DecimalNum statistic of 1e400 is finite in its own factory but
        // overflows to +Infinity when coerced into a DoubleNum context: the
        // sizer saturates to the epsilon floor instead of failing open.
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> decimalStatistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(new BigDecimal("1e400")));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(decimalStatistic, 100, 10);

        runWithNumFactory(DoubleNumFactory.getInstance(), () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            assertNumEquals(numFactory.epsilon(), sizer.amount(context(series, 1, 1)));
        });
    }

    @Test
    public void exactDecimalCoercionPreservesRatioOutsideDoubleRange() {
        // A finite DecimalNum statistic of 1e400 coerced into a distinct
        // DecimalNumFactory context keeps its magnitude: the representable
        // damped amount 100 / (1 + 1e400) = 1e-398 must be returned instead
        // of the double-overflow epsilon floor, which only applies to
        // double-backed contexts.
        NumFactory precisionFiftyFactory = DecimalNumFactory.getInstance(50);
        NumFactory precisionThirtyFactory = DecimalNumFactory.getInstance(30);
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(precisionFiftyFactory)
                .withData(1, 2)
                .build();
        FixedIndicator<Num> decimalStatistic = new FixedIndicator<>(decimalSeries, precisionFiftyFactory.numOf(0),
                precisionFiftyFactory.numOf(new BigDecimal("1e400")));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(decimalStatistic, 100, 1);

        runWithNumFactory(precisionThirtyFactory, () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            // Both operands underflow the double representation, so the exact
            // BigDecimal value is what discriminates from the epsilon floor.
            assertNumEquals(numFactory.numOf(new BigDecimal("1e-398")), sizer.amount(context(series, 1, 1)), 0);
        });
    }

    @Test
    public void positiveUnderflowingBaseAmountSaturatesToEpsilonFloor() {
        // A DecimalNum base amount of 1e-400 is positive in its own factory
        // but underflows to zero when coerced into a DoubleNum context: the
        // sizer saturates it to the context epsilon instead of returning zero
        // and aborting BarSeriesManager validation.
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> decimalStatistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(5));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(decimalStatistic, new BigDecimal("1e-400"), 10);

        runWithNumFactory(DoubleNumFactory.getInstance(), () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            Num amount = sizer.amount(context(series, 1, 1));
            assertTrue(amount.isPositive());
            assertTrue(amount.isLessThanOrEqual(numFactory.epsilon()));
        });
    }

    @Test
    public void zeroStatisticReturnsFullBaseAmountDespiteUnderflowedControlLimit() {
        // A DecimalNum control limit of 1e-400 underflows to zero in a
        // DoubleNum context; a zero statistic is exactly safe and must size
        // at the full base amount rather than falling through a NaN ratio to
        // the epsilon fallback.
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> decimalStatistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(0));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(decimalStatistic, 100, new BigDecimal("1e-400"));

        runWithNumFactory(DoubleNumFactory.getInstance(), () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            assertNumEquals(100, sizer.amount(context(series, 1, 1)));
        });
    }

    @Test
    public void positiveOverflowingBaseAmountSaturatesToMaxFiniteValue() {
        // A DecimalNum base amount of 1e400 is positive in its own factory
        // but overflows to +Infinity when coerced into a DoubleNum context:
        // the sizer saturates it to the largest finite context value instead
        // of returning infinity and aborting BarSeriesManager validation.
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> decimalStatistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(1));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(decimalStatistic, new BigDecimal("1e400"), 10);

        runWithNumFactory(DoubleNumFactory.getInstance(), () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            Num amount = sizer.amount(context(series, 1, 1));
            assertTrue(Num.isFinite(amount));
            assertTrue(amount.isGreaterThan(numOf(Double.MAX_VALUE / 2)));
        });
    }

    @Test
    public void underflowedPositiveStatisticKeepsExactRatio() {
        // A positive DecimalNum statistic of 1e-400 underflows to zero in a
        // DoubleNum context; with the control limit also 1e-400 the true
        // standardized ratio is exactly 1, so the sizer must size at half
        // the base amount instead of mistaking the coerced zero for an
        // exactly safe process and returning the full amount.
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> decimalStatistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(new BigDecimal("1e-400")));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(decimalStatistic, 100, new BigDecimal("1e-400"));

        runWithNumFactory(DoubleNumFactory.getInstance(), () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            assertNumEquals(50, sizer.amount(context(series, 1, 1)));
        });
    }

    @Test
    public void negativeOverflowCoercionFailsOpen() {
        // A DecimalNum statistic of -1e400 coerces to -Infinity in a DoubleNum
        // context: max(0, -Inf) would be zero, so the sizer fails open.
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> decimalStatistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(new BigDecimal("-1e400")));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(decimalStatistic, 100, 10);

        runWithNumFactory(DoubleNumFactory.getInstance(), () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            assertNumEquals(100, sizer.amount(context(series, 1, 1)));
        });
    }

    @Test
    public void floatBackedContextSaturatesOverflowingSafePathAmount() {
        // A float-backed context factory overflows a coerced 1e39 base amount
        // to a non-finite value; the safe path returns it unchanged, so the
        // final double-range cap must saturate at the float ceiling instead
        // of handing BarSeriesManager a non-finite amount.
        NumFactory floatBackedFactory = new NumFactory() {
            private final NumFactory delegate = DoubleNumFactory.getInstance();

            @Override
            public Num minusOne() {
                return delegate.minusOne();
            }

            @Override
            public Num zero() {
                return delegate.zero();
            }

            @Override
            public Num one() {
                return delegate.one();
            }

            @Override
            public Num two() {
                return delegate.two();
            }

            @Override
            public Num three() {
                return delegate.three();
            }

            @Override
            public Num hundred() {
                return delegate.hundred();
            }

            @Override
            public Num thousand() {
                return delegate.thousand();
            }

            @Override
            public Num numOf(Number number) {
                return Math.abs(number.doubleValue()) > Float.MAX_VALUE ? NaN.NaN : delegate.numOf(number);
            }

            @Override
            public Num numOf(String number) {
                return numOf(Double.valueOf(number));
            }
        };
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0), NaN.NaN);
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, 1e39, 10);

        BarSeries series = new MockBarSeriesBuilder().withNumFactory(floatBackedFactory).withData(1, 2).build();
        Num amount = sizer.amount(context(series, 1, 1));

        assertTrue(Num.isFinite(amount));
        assertNumEquals(Float.MAX_VALUE, amount);
    }

    @Test
    public void decimalSafePathSaturatesBaseAmountBeyondDoubleRange() {
        // A DecimalNum base amount of 1e400 is positive in its own factory and
        // the safe path returns it unchanged, but its doubleValue() is
        // infinity: the final cap must saturate at the double ceiling for
        // BarSeriesManager's double-based validation.
        runWithNumFactory(DECIMAL_NUM_FACTORY, () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            FixedIndicator<Num> statistic = new FixedIndicator<>(series, numFactory.numOf(0), NaN.NaN);
            PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("1e400"), 10);

            Num amount = sizer.amount(context(series, 1, 1));

            assertTrue(Double.isFinite(amount.doubleValue()));
            assertTrue(amount.isGreaterThanOrEqual(numFactory.numOf(Double.MAX_VALUE / 2)));
        });
    }

    @Test
    public void decimalDampedPathSaturatesAmountBeyondDoubleRange() {
        // With baseAmount and controlLimit both 1e400 in a BigDecimal-backed
        // context the damped amount stays at 1e400: Num-finite, but its
        // doubleValue() is infinity, so the final cap must saturate at the
        // double ceiling for BarSeriesManager's double-based validation.
        runWithNumFactory(DECIMAL_NUM_FACTORY, () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            FixedIndicator<Num> statistic = new FixedIndicator<>(series, numFactory.numOf(0), numFactory.numOf(5));
            PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("1e400"),
                    new BigDecimal("1e400"));

            Num amount = sizer.amount(context(series, 1, 1));

            assertTrue(Double.isFinite(amount.doubleValue()));
            assertTrue(amount.isGreaterThanOrEqual(numFactory.numOf(Double.MAX_VALUE / 2)));
        });
    }

    @Test
    public void rejectsInvalidParameters() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(series, numOf(0), numOf(5), numOf(15));

        assertThrows(NullPointerException.class, () -> new ProcessCapabilityPositionSizer(null, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> new ProcessCapabilityPositionSizer(statistic, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> new ProcessCapabilityPositionSizer(statistic, 100, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ProcessCapabilityPositionSizer(statistic, 100, Double.NaN));
    }

    private PositionSizer.Context context(BarSeries series, int signalIndex, int entryIndex) {
        Strategy strategy = new BaseStrategy(new FixedRule(), new FixedRule());
        TradingRecord tradingRecord = new BaseTradingRecord();
        return new PositionSizer.Context(signalIndex, entryIndex, numOf(1), strategy, series, TradeType.BUY,
                tradingRecord, new ZeroCostModel(), new ZeroCostModel());
    }
}
