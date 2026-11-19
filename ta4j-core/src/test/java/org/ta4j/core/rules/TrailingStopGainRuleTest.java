/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Rule;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TrailingStopGainRuleTest extends AbstractIndicatorTest<Object, Object> {

    public TrailingStopGainRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void isSatisfiedForBuy() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 110, 120, 130, 117.00, 130,
                116.99);

        TrailingStopGainRule rule = new TrailingStopGainRule(closePrice, numOf(10));
        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        tradingRecord.enter(2, numOf(114), numOf(1));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));

        tradingRecord.exit(5);

        tradingRecord.enter(5, numOf(128), numOf(1));
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertFalse(rule.isSatisfied(6, tradingRecord));
    }

    @Test
    public void doesNotTriggerBeforeGainActivationForBuy() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 104, 96, 93);

        TrailingStopGainRule rule = new TrailingStopGainRule(closePrice, numOf(10));
        tradingRecord.enter(0, numOf(100), numOf(1));

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void isSatisfiedForBuyForBarCount() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 110, 120, 130, 120, 117, 117);

        TrailingStopGainRule rule = new TrailingStopGainRule(closePrice, numOf(10), 3);
        tradingRecord.enter(2, numOf(114), numOf(1));

        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertFalse(rule.isSatisfied(4, tradingRecord));
        assertTrue(rule.isSatisfied(5, tradingRecord));
        assertFalse(rule.isSatisfied(6, tradingRecord));
    }

    @Test
    public void isNotSatisfiedBeforeEntryIndex() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 110, 120);
        TrailingStopGainRule rule = new TrailingStopGainRule(closePrice, numOf(10));

        tradingRecord.enter(2, numOf(120), numOf(1));

        assertFalse(rule.isSatisfied(1, tradingRecord));
    }

    @Test
    public void clampsTrailingWindowToRetainedBarsForBuy() {
        MockBarSeriesBuilder builder = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 120, 130, 117, 116);
        builder.withMaxBarCount(3);
        BarSeries series = builder.build();
        ClosePriceIndicator closePrice = StopRuleTestSupport.strictClosePrice(series);
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        TrailingStopGainRule rule = new TrailingStopGainRule(closePrice, numOf(10));

        tradingRecord.enter(0, numOf(100), numOf(1));

        assertTrue(rule.isSatisfied(5, tradingRecord));
        assertTrue(rule.stopPrice(series, tradingRecord.getCurrentPosition()).isEqual(numOf(117)));
    }

    @Test
    public void isSatisfiedForSell() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 90, 80, 70, 77.00, 120,
                132.01);

        TrailingStopGainRule rule = new TrailingStopGainRule(closePrice, numOf(10));
        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        tradingRecord.enter(2, numOf(84), numOf(1));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));

        tradingRecord.exit(5);

        tradingRecord.enter(5, numOf(128), numOf(1));
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertFalse(rule.isSatisfied(6, tradingRecord));
    }

    @Test
    public void doesNotTriggerBeforeGainActivationForSell() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 96, 104, 107);

        TrailingStopGainRule rule = new TrailingStopGainRule(closePrice, numOf(10));
        tradingRecord.enter(0, numOf(100), numOf(1));

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void expiredExtremeCannotActivateTrailingGain() {
        ClosePriceIndicator close = StopRuleTestSupport.closePrice(numFactory, 100, 120, 108, 105, 104);
        TrailingStopGainRule rule = new TrailingStopGainRule(close, numOf(10), 3);
        BaseTradingRecord record = new BaseTradingRecord();
        record.enter(0, numOf(100), numOf(1));
        assertFalse(rule.isSatisfied(1, record));
        assertTrue(rule.isSatisfied(2, record));
        assertTrue(rule.isSatisfied(3, record));
        assertFalse(rule.isSatisfied(4, record));
    }

    @Test
    public void defaultTrailingStopsMatchRetainedScansWithLinearBarReads() {
        int length = 192;
        for (int capacity : new int[] { 8, 48 }) {
            for (TradeType side : TradeType.values()) {
                BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
                series.setMaximumBarCount(capacity);
                AtomicInteger reads = new AtomicInteger();
                BarSeries countedSeries = interceptBarReads(series, ignored -> reads.incrementAndGet());
                ClosePriceIndicator close = new ClosePriceIndicator(countedSeries);
                ConstantIndicator<Num> volatility = new ConstantIndicator<>(countedSeries, numOf(10));
                Rule[] rules = { new TrailingStopLossRule(close, numOf(10)), new TrailingStopGainRule(close, numOf(10)),
                        new TrailingFixedAmountStopLossRule(close, numOf(10)),
                        new TrailingFixedAmountStopGainRule(close, numOf(10)),
                        new VolatilityTrailingStopLossRule(close, volatility),
                        new VolatilityTrailingStopGainRule(close, volatility) };
                BaseTradingRecord record = new BaseTradingRecord(side);
                record.enter(0, numOf(100), numOf(1));
                for (int index = 0; index < length; index++) {
                    series.barBuilder().closePrice(index == 0 ? 100 : 80 + (index * 17) % 41).add();
                    assertTrailingStopsMatchScan(series, record, rules, side == TradeType.BUY, index);
                    if (index % 23 == 0) {
                        series.addPrice(numOf(80 + (index * 11) % 41));
                        assertTrailingStopsMatchScan(series, record, rules, side == TradeType.BUY, index);
                    }
                }
                // The proxy also counts the cache's terminal-bar fingerprints.
                assertTrue(reads.get() <= 10 * rules.length * length, "bar reads must grow linearly, capacity="
                        + capacity + ", side=" + side + ", reads=" + reads.get());
            }
        }
    }

    private void assertTrailingStopsMatchScan(BarSeries series, BaseTradingRecord record, Rule[] rules, boolean buy,
            int index) {
        Num extreme = series.getBar(series.getBeginIndex()).getClosePrice();
        for (int retained = series.getBeginIndex() + 1; retained <= index; retained++) {
            Num value = series.getBar(retained).getClosePrice();
            extreme = buy ? extreme.max(value) : extreme.min(value);
        }
        Num current = series.getBar(index).getClosePrice();
        Num entry = numOf(100);
        Num amount = numOf(10);
        Num anchoredExtreme = buy ? entry.max(extreme) : entry.min(extreme);
        boolean percentageActivated = buy ? !extreme.isLessThan(StopGainRule.stopGainPrice(entry, amount, true))
                : !extreme.isGreaterThan(StopGainRule.stopGainPrice(entry, amount, false));
        boolean amountActivated = buy ? !extreme.isLessThan(StopGainRule.stopGainPriceFromDistance(entry, amount, true))
                : !extreme.isGreaterThan(StopGainRule.stopGainPriceFromDistance(entry, amount, false));
        boolean[] expected = { reachedStop(current, StopLossRule.stopLossPrice(extreme, amount, buy), buy),
                percentageActivated
                        && reachedStop(current, StopGainRule.trailingStopGainPrice(extreme, amount, buy), buy),
                reachedStop(current, StopLossRule.stopLossPriceFromDistance(extreme, amount, buy), buy),
                amountActivated && reachedStop(current,
                        StopGainRule.trailingStopGainPriceFromDistance(extreme, amount, buy), buy),
                reachedStop(current, StopLossRule.stopLossPriceFromDistance(anchoredExtreme, amount, buy), buy),
                amountActivated && reachedStop(current,
                        StopGainRule.trailingStopGainPriceFromDistance(anchoredExtreme, amount, buy), buy) };
        for (int rule = 0; rule < rules.length; rule++) {
            assertEquals(expected[rule], rules[rule].isSatisfied(index, record),
                    rules[rule].getClass().getSimpleName() + " at index " + index + " buy=" + buy);
        }
    }

    private boolean reachedStop(Num current, Num stop, boolean buy) {
        return buy ? current.isLessThanOrEqual(stop) : current.isGreaterThanOrEqual(stop);
    }

    private BarSeries interceptBarReads(BarSeries delegate, IntConsumer beforeRead) {
        return (BarSeries) Proxy.newProxyInstance(BarSeries.class.getClassLoader(), new Class<?>[] { BarSeries.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getBar")) {
                        beforeRead.accept((Integer) args[0]);
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    @Test
    public void serializeAndDeserialize() {
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 110, 90, 95, 105);
        TrailingStopGainRule rule = new TrailingStopGainRule(closePrice, numOf(7), 2);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(closePrice.getBarSeries(), rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(closePrice.getBarSeries(), rule);
    }

    @Test
    public void constructorValidation() {
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 101);
        assertThrows(NullPointerException.class, () -> new TrailingStopGainRule(null, numFactory.numOf(10), 2));
        new TrailingStopGainRule(closePrice, numFactory.zero(), 2);
        assertThrows(IllegalArgumentException.class, () -> new TrailingStopGainRule(closePrice, NaN.NaN, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new TrailingStopGainRule(closePrice, numFactory.minusOne(), 2));
        assertThrows(IllegalArgumentException.class,
                () -> new TrailingStopGainRule(closePrice, numFactory.numOf(10), 0));
    }
}
