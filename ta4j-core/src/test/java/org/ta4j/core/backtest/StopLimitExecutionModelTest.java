/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

public class StopLimitExecutionModelTest extends AbstractIndicatorTest<BarSeries, Num> {

    public StopLimitExecutionModelTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void rejectsUntriggeredOrderWhenItExpires() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(102d).lowPrice(98d).closePrice(100d).volume(10d).add();
        series.barBuilder().openPrice(100d).highPrice(103d).lowPrice(97d).closePrice(101d).volume(10d).add();
        series.barBuilder().openPrice(101d).highPrice(104d).lowPrice(98d).closePrice(100d).volume(10d).add();

        StopLimitExecutionModel model = new StopLimitExecutionModel(numOf(0.05), numOf(0.06), numOf(0.5), 2);
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule());
        TradingRecord tradingRecord = new BarSeriesManager(series, model).run(strategy);

        assertTrue(tradingRecord.getTrades().isEmpty());
        assertFalse(model.getPendingOrder(tradingRecord).isPresent());
        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
        assertTrue(rejection.reason().contains("expired"));
        assertEquals(series.numFactory().zero(), rejection.filledAmount());
    }

    @Test
    public void aggregatesPartialFillsAcrossBarsUntilRequestedAmountIsReached() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(100d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(6d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(8d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(6d).add();

        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numOf(0.5),
                4);
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule());
        TradingRecord tradingRecord = new BarSeriesManager(series, model).run(strategy, strategy.getStartingType(),
                numFactory.numOf(10));

        assertEquals(1, tradingRecord.getTrades().size());
        Trade entry = tradingRecord.getTrades().getFirst();
        assertEquals(numFactory.numOf(10), entry.getAmount());
        assertEquals(3, entry.getFills().size());
        assertEquals(numFactory.numOf(3), entry.getFills().get(0).amount());
        assertEquals(numFactory.numOf(4), entry.getFills().get(1).amount());
        assertEquals(numFactory.numOf(3), entry.getFills().get(2).amount());
        assertFalse(model.getPendingOrder(tradingRecord).isPresent());
        assertTrue(model.getRejectedOrders(tradingRecord).isEmpty());
    }

    @Test
    public void recordsFilledPortionAndRejectsRemainingAmountOnExpiry() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(100d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(4d).add();
        series.barBuilder().openPrice(100d).highPrice(101d).lowPrice(99d).closePrice(100d).volume(2d).add();

        StopLimitExecutionModel model = new StopLimitExecutionModel(numFactory.zero(), numFactory.zero(), numOf(0.5),
                2);
        Strategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule());
        TradingRecord tradingRecord = new BarSeriesManager(series, model).run(strategy, strategy.getStartingType(),
                numFactory.numOf(5));

        assertEquals(1, tradingRecord.getTrades().size());
        Trade trade = tradingRecord.getTrades().getFirst();
        assertEquals(numFactory.numOf(3), trade.getAmount());
        assertEquals(2, trade.getFills().size());
        TradeFill firstFill = trade.getFills().get(0);
        TradeFill secondFill = trade.getFills().get(1);
        assertEquals(numFactory.numOf(2), firstFill.amount());
        assertEquals(numFactory.one(), secondFill.amount());

        assertEquals(1, model.getRejectedOrders(tradingRecord).size());
        StopLimitExecutionModel.RejectedOrder rejection = model.getRejectedOrders(tradingRecord).getFirst();
        assertEquals(numFactory.numOf(5), rejection.requestedAmount());
        assertEquals(numFactory.numOf(3), rejection.filledAmount());
    }
}
