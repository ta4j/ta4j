/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
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
    public void limitOutsideCapabilityFactoryRangeSizesAgainstContextFactory() {
        // A control limit representable only by the sizing context (a
        // DecimalNum context carries 1e400) must be accepted even when the
        // capability factory (DoubleNum) cannot represent it: the configured
        // limit is retained losslessly and coerced through the context factory
        // at sizing time instead of being validated against the capability
        // factory's range in the constructor.
        BarSeries capabilitySeries = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(1, 2)
                .build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(capabilitySeries, DoubleNumFactory.getInstance().numOf(0),
                DoubleNumFactory.getInstance().numOf(1));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, 100, new BigDecimal("1e400"));

        runWithNumFactory(DECIMAL_NUM_FACTORY, () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            // DecimalNum divides the statistic ratio 1/1e400 exactly; the
            // denominator 1 + 1e-400 rounds back to 1, leaving the full base
            // amount.
            assertNumEquals(numFactory.numOf(100), sizer.amount(context(series, 1, 1)));
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
    public void coarseIndicatorFactoryStandardizesAtDestinationPrecision() {
        // A precision-1 capability factory computes 1 / 3 as 0.3 before the
        // precision-10 context ever sees the ratio, damping a base of 100 to
        // about 76.923. Coercing the operands into the context first keeps
        // the ratio at the destination precision: precision-10 arithmetic
        // evaluates 100 / (1 + 1/3) as 75.00000002.
        NumFactory precisionOneFactory = DecimalNumFactory.getInstance(1);
        NumFactory precisionTenFactory = DecimalNumFactory.getInstance(10);
        BarSeries indicatorSeries = new MockBarSeriesBuilder().withNumFactory(precisionOneFactory)
                .withData(1, 2)
                .build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(indicatorSeries, precisionOneFactory.numOf(0),
                precisionOneFactory.numOf(1));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, 100, 3);

        runWithNumFactory(precisionTenFactory, () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            assertNumEquals(numFactory.numOf(new BigDecimal("75.00000002")), sizer.amount(context(series, 1, 1)), 0);
        });
    }

    @Test
    public void directDampingAvoidsReciprocalRounding() {
        NumFactory precisionTenFactory = DecimalNumFactory.getInstance(10);
        BarSeries indicatorSeries = new MockBarSeriesBuilder().withNumFactory(precisionTenFactory)
                .withData(1, 2)
                .build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(indicatorSeries, precisionTenFactory.numOf(0),
                precisionTenFactory.numOf(new BigDecimal("3.141592653")));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("0.2"), BigDecimal.ONE);

        runWithNumFactory(precisionTenFactory, () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            assertNumEquals(numFactory.numOf(new BigDecimal("0.04829060141")), sizer.amount(context(series, 1, 1)), 0);
        });
    }

    @Test
    public void controlLimitSurvivesCoarseCapabilityFactoryRounding() {
        // A precision-1 capability factory collapses the configured limit
        // 3.14159 to 3; sizing must coerce the lossless raw limit through the
        // context factory, matching a precision-10 indicator's result exactly.
        NumFactory precisionOneFactory = DecimalNumFactory.getInstance(1);
        NumFactory precisionTenFactory = DecimalNumFactory.getInstance(10);
        BarSeries coarseSeries = new MockBarSeriesBuilder().withNumFactory(precisionOneFactory).withData(1, 2).build();
        FixedIndicator<Num> coarseStatistic = new FixedIndicator<>(coarseSeries, precisionOneFactory.numOf(0),
                precisionOneFactory.numOf(1));
        PositionSizer coarseSizer = new ProcessCapabilityPositionSizer(coarseStatistic, 100, new BigDecimal("3.14159"));
        BarSeries fineSeries = new MockBarSeriesBuilder().withNumFactory(precisionTenFactory).withData(1, 2).build();
        FixedIndicator<Num> fineStatistic = new FixedIndicator<>(fineSeries, precisionTenFactory.numOf(0),
                precisionTenFactory.numOf(1));
        PositionSizer fineSizer = new ProcessCapabilityPositionSizer(fineStatistic, 100, new BigDecimal("3.14159"));

        runWithNumFactory(precisionTenFactory, () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            assertNumEquals(fineSizer.amount(context(series, 1, 1)), coarseSizer.amount(context(series, 1, 1)));
        });
    }

    @Test
    public void lossyContextCoercionFallsBackToRawControlLimit() {
        // A precision-1 capability factory rounds the configured limit 3.14159
        // to 3; when the context coercion of the statistic saturates (1e400 in
        // a DoubleNum context), the fallback ratio must divide the lossless raw
        // forms so the damped amount reflects 3.14159, not the rounded 3.
        NumFactory precisionOneFactory = DecimalNumFactory.getInstance(1);
        BarSeries indicatorSeries = new MockBarSeriesBuilder().withNumFactory(precisionOneFactory)
                .withData(1, 2)
                .build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(indicatorSeries, precisionOneFactory.numOf(0),
                precisionOneFactory.numOf(new BigDecimal("1e400")));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("1e400"),
                new BigDecimal("3.14159"));

        runWithNumFactory(DoubleNumFactory.getInstance(), () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            BigDecimal expected = new BigDecimal("1e400").divide(
                    BigDecimal.ONE
                            .add(new BigDecimal("1e400").divide(new BigDecimal("3.14159"), MathContext.DECIMAL128)),
                    MathContext.DECIMAL128);
            assertNumEquals(numFactory.numOf(expected), sizer.amount(context(series, 1, 1)));
        });
    }

    @Test
    public void recoveredQuotientUnderflowingTheContextFactoryFloorsAtEpsilon() {
        // 1e160 / 1e-160 overflows double; the decimal-damped quotient (~1e-50)
        // underflows a float-backed context to zero and floors at epsilon.
        NumFactory doubleNumFactory = DoubleNumFactory.getInstance();
        BarSeries indicatorSeries = new MockBarSeriesBuilder().withNumFactory(doubleNumFactory).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(indicatorSeries, doubleNumFactory.numOf(0),
                doubleNumFactory.numOf(new BigDecimal("1e160")));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("1e270"),
                new BigDecimal("1e-160"));

        BarSeries series = new MockBarSeriesBuilder().withNumFactory(floatBackedFactory()).withData(1, 2).build();
        Num amount = sizer.amount(context(series, 1, 1));

        assertTrue(amount.isPositive());
        assertNumEquals(floatBackedFactory().epsilon(), amount);
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
    public void configuredBaseAmountSurvivesCapabilityFactoryPrecision() {
        // The constructor must retain the configured base amount losslessly:
        // a precision-1 capability factory rounds 1.2345 to 1, and coercing
        // that already-rounded copy into a precision-10 context would
        // permanently lose the configured digits. The raw snapshot must
        // round through the context factory instead.
        NumFactory precisionOneFactory = DecimalNumFactory.getInstance(1);
        NumFactory precisionTenFactory = DecimalNumFactory.getInstance(10);
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(precisionOneFactory).withData(1, 2).build();
        FixedIndicator<Num> decimalStatistic = new FixedIndicator<>(decimalSeries, precisionOneFactory.numOf(0),
                precisionOneFactory.numOf(0));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(decimalStatistic, new BigDecimal("1.2345"), 10);

        runWithNumFactory(precisionTenFactory, () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            // A zero statistic is exactly safe and returns the full base
            // amount unchanged, so the assertion isolates the base-amount
            // coercion.
            assertNumEquals(numFactory.numOf(new BigDecimal("1.2345")), sizer.amount(context(series, 1, 1)), 0);
        });
    }

    @Test
    public void integralBaseAmountsConvertWithoutDoubleRounding() {
        // Long.MAX_VALUE and a large BigInteger convert directly to
        // BigDecimal, so no precision is lost through a doubleValue() round
        // trip (which would round Long.MAX_VALUE up by one ulp).
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(series, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(0));
        PositionSizer longSizer = new ProcessCapabilityPositionSizer(statistic, Long.MAX_VALUE, 10);
        PositionSizer bigIntegerSizer = new ProcessCapabilityPositionSizer(statistic,
                new BigInteger("10000000000000000000000001"), 10);

        runWithNumFactory(DECIMAL_NUM_FACTORY, () -> {
            // Zero statistics are exactly safe and return the full base amount
            // unchanged, isolating the conversion.
            assertNumEquals(numFactory.numOf(BigDecimal.valueOf(Long.MAX_VALUE)),
                    longSizer.amount(context(series, 1, 1)), 0);
            assertNumEquals(numFactory.numOf(new BigDecimal("10000000000000000000000001")),
                    bigIntegerSizer.amount(context(series, 1, 1)), 0);
        });
    }

    @Test
    public void overflowingBaseAmountSaturatesInsteadOfEpsilonFallback() {
        // DoubleNumFactory converts an oversized BigDecimal base amount to
        // positive infinity; the same-factory fast path must not publish
        // that non-finite value. The true damped amount (1e400 / 1.5) lies
        // beyond the primitive double range, so the sizer saturates at the
        // factory ceiling instead of damping the lossy capped base or
        // falling back to the epsilon floor.
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(series, numOf(0), numOf(5));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("1e400"), 10);

        Num amount = sizer.amount(context(series, 1, 1));
        assertNumEquals(numOf(Double.MAX_VALUE), amount, 0);
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
        NumFactory floatBackedFactory = floatBackedFactory();
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0), NaN.NaN);
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, 1e39, 10);

        BarSeries series = new MockBarSeriesBuilder().withNumFactory(floatBackedFactory).withData(1, 2).build();
        Num amount = sizer.amount(context(series, 1, 1));

        assertTrue(Num.isFinite(amount));
        assertNumEquals(Float.MAX_VALUE, amount);
    }

    @Test
    public void narrowContextPreservesRepresentableDampedQuotient() {
        // A DecimalNum statistic/controlLimit ratio of 1e39 is finite as a
        // primitive double but overflows a float-backed context factory. The
        // damped quotient (baseAmount 1e38 over the 1e39 ratio) is
        // representable, so the sizer must compute it in double space and
        // coerce the result instead of collapsing to the context epsilon.
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(new BigDecimal("1e39")));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("1e38"), 1);

        BarSeries series = new MockBarSeriesBuilder().withNumFactory(floatBackedFactory()).withData(1, 2).build();

        assertNumEquals(0.1, sizer.amount(context(series, 1, 1)));
    }

    @Test
    public void overflowingBaseDampsInDoubleSpaceInsteadOfPublishingNonFiniteAmount() {
        // A DecimalNum base amount of 1e39 overflows a float-backed context
        // factory to a non-finite value, but the damped quotient (1e39 / 6) is
        // representable: the sizer must compute it in double space and coerce
        // the result instead of publishing a non-finite amount.
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(5));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("1e39"), 1);

        BarSeries series = new MockBarSeriesBuilder().withNumFactory(floatBackedFactory()).withData(1, 2).build();
        Num amount = sizer.amount(context(series, 1, 1));

        assertTrue(Num.isFinite(amount));
        assertNumEquals(numOf(1e39 / 6.0), amount);
    }

    @Test
    public void overflowingBaseBeyondDoubleRangeSaturatesAtContextCeiling() {
        // A DecimalNum base of 1e400 overflows both the float-backed context
        // factory and the primitive double range, so the true damped quotient
        // is beyond the factory ceiling: the sizer must saturate at the float
        // ceiling instead of publishing a non-finite amount.
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(5));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("1e400"), 1);

        BarSeries series = new MockBarSeriesBuilder().withNumFactory(floatBackedFactory()).withData(1, 2).build();
        Num amount = sizer.amount(context(series, 1, 1));

        assertTrue(Num.isFinite(amount));
        assertNumEquals(numOf(Float.MAX_VALUE), amount);
    }

    @Test
    public void coarseDecimalContextSaturatesBeyondDoubleRangeAtTrueCeiling() {
        // A precision-2 DecimalNum context rounds Double.MAX_VALUE
        // (1.7977e308) up to 1.8e308, whose doubleValue is non-finite, so the
        // largest context value that still round-trips to a finite double is
        // 1.7e308. The saturated amount must publish that ceiling instead of
        // the conservative 9.0e307 a Double.MAX_VALUE / 2 fallback yields.
        NumFactory precisionTwoFactory = DecimalNumFactory.getInstance(2);
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(0));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("1e400"), 10);

        BarSeries series = new MockBarSeriesBuilder().withNumFactory(precisionTwoFactory).withData(1, 2).build();
        Num amount = sizer.amount(context(series, 1, 1));

        assertTrue(Num.isFinite(amount));
        assertEquals(1.7e308, amount.doubleValue(), 0.0);
    }

    @Test
    public void overflowingIndicatorRatioRecomputesInWiderContextFactory() {
        // DoubleNum cannot represent Double.MAX_VALUE / Double.MIN_VALUE (the
        // true ratio is about 3.6e631), so the standardized statistic
        // overflows the indicator's factory. A DecimalNum context can hold the
        // ratio: the sizer must re-derive it in the context factory and return
        // the representable damped amount instead of the epsilon floor.
        BarSeries indicatorSeries = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(1, 2)
                .build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(indicatorSeries, DoubleNumFactory.getInstance().numOf(0),
                DoubleNumFactory.getInstance().numOf(Double.MAX_VALUE));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, 1, Double.MIN_VALUE);

        runWithNumFactory(DECIMAL_NUM_FACTORY, () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            Num amount = sizer.amount(context(series, 1, 1));

            assertTrue(Num.isFinite(amount));
            assertTrue(amount.isPositive());
            assertTrue(amount.isLessThan(numFactory.one()));
            assertTrue(amount.doubleValue() == 0.0);
        });
    }

    @Test
    public void dampedQuotientRecoversFromExactDecimalsWhenRatioExceedsDoubleRange() {
        // A DecimalNum base of 1e400 and a statistic of 1e400 with a control
        // limit of 1 standardize to a ratio beyond the primitive double range,
        // but the damped quotient 1e400 / (1 + 1e400) is about 1: the sizer
        // must divide the exact decimal forms before narrowing to the
        // double-backed context instead of collapsing to the epsilon floor.
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(new BigDecimal("1e400")));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("1e400"), 1);

        runWithNumFactory(DoubleNumFactory.getInstance(), () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            Num amount = sizer.amount(context(series, 1, 1));

            assertTrue(Num.isFinite(amount));
            assertNumEquals(numFactory.numOf(1), amount, 0);
        });
    }

    @Test
    public void oversizedBaseWithFiniteRatioDampsInDecimalSpaceBeforeNarrowing() {
        // A DecimalNum base of 1e400 with a finite standardized ratio of 1e100
        // in a DoubleNum context: the coerced base saturates at
        // Double.MAX_VALUE, whose damping (MAX_VALUE / (1 + 1e100), about
        // 1.8e208) is far below the true quotient 1e400 / (1 + 1e100), about
        // 1e300. The sizer must divide the lossless decimal forms before
        // narrowing the result.
        BarSeries decimalSeries = new MockBarSeriesBuilder().withNumFactory(DECIMAL_NUM_FACTORY).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(decimalSeries, DECIMAL_NUM_FACTORY.numOf(0),
                DECIMAL_NUM_FACTORY.numOf(new BigDecimal("1e100")));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("1e400"), 1);

        runWithNumFactory(DoubleNumFactory.getInstance(), () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
            Num amount = sizer.amount(context(series, 1, 1));

            assertTrue(Num.isFinite(amount));
            assertNumEquals(numFactory.numOf(1e300), amount, 0);
        });
    }

    @Test
    public void overflowingRatioWithOversizedBaseDampsBeforeFlooring() {
        // statistic == Double.MAX_VALUE over controlLimit ==
        // Double.MIN_VALUE overflows both factories (the ratio is about
        // 3.67e631), but the damped quotient base / (1 + ratio) is about
        // 2.73e-232: representable. The sizer must divide the lossless
        // decimal forms of all three operands instead of flooring at the
        // context epsilon; with a still-larger base the quotient overflows
        // and the honest result saturates at the factory ceiling.
        BarSeries doubleSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
        FixedIndicator<Num> statistic = new FixedIndicator<>(doubleSeries, numOf(0), numOf(Double.MAX_VALUE));
        PositionSizer sizer = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("1e400"), Double.MIN_VALUE);
        PositionSizer overflowing = new ProcessCapabilityPositionSizer(statistic, new BigDecimal("1e1000"),
                Double.MIN_VALUE);

        runWithNumFactory(DoubleNumFactory.getInstance(), () -> {
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();

            Num amount = sizer.amount(context(series, 1, 1));

            assertTrue(Num.isFinite(amount));
            assertTrue(amount.isPositive());
            BigDecimal ratio = BigDecimal.valueOf(Double.MAX_VALUE)
                    .divide(BigDecimal.valueOf(Double.MIN_VALUE), MathContext.DECIMAL128);
            BigDecimal expected = new BigDecimal("1e400").divide(BigDecimal.ONE.add(ratio, MathContext.DECIMAL128),
                    MathContext.DECIMAL128);
            assertNumEquals(numFactory.numOf(expected), amount, 0);

            Num saturated = overflowing.amount(context(series, 1, 1));
            assertTrue(Double.isFinite(saturated.doubleValue()));
            assertTrue(saturated.isGreaterThanOrEqual(numFactory.numOf(Double.MAX_VALUE / 2)));
        });
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

    /**
     * A float-range factory: magnitudes above the float ceiling coerce to a
     * non-finite value and subnormal magnitudes to zero, mimicking a float-backed
     * context in an otherwise double-based test suite.
     */
    private NumFactory floatBackedFactory() {
        return new NumFactory() {
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
                double value = number.doubleValue();
                if (Math.abs(value) > Float.MAX_VALUE) {
                    return NaN.NaN;
                }
                if (value != 0 && Math.abs(value) < Float.MIN_VALUE) {
                    return delegate.zero();
                }
                return delegate.numOf(number);
            }

            @Override
            public Num numOf(String number) {
                return numOf(Double.valueOf(number));
            }
        };
    }

    private PositionSizer.Context context(BarSeries series, int signalIndex, int entryIndex) {
        Strategy strategy = new BaseStrategy(new FixedRule(), new FixedRule());
        TradingRecord tradingRecord = new BaseTradingRecord();
        return new PositionSizer.Context(signalIndex, entryIndex, numOf(1), strategy, series, TradeType.BUY,
                tradingRecord, new ZeroCostModel(), new ZeroCostModel());
    }
}
