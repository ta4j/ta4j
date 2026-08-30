/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertThrows;
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
