/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class StopLossRuleTest extends AbstractIndicatorTest<BarSeries, Num> {

    private ClosePriceIndicator closePrice;

    public StopLossRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        closePrice = new ClosePriceIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 120, 100, 150, 110, 100)
                .build());
    }

    @Test
    public void stopLossPriceCalculatesThresholds() {
        Num entryPrice = numFactory.hundred();
        Num lossPercent = numFactory.numOf(5);

        assertNumEquals(95, StopLossRule.stopLossPrice(entryPrice, lossPercent, true));
        assertNumEquals(105, StopLossRule.stopLossPrice(entryPrice, lossPercent, false));
        assertNumEquals(95, StopLossRule.stopLossPriceFromDistance(entryPrice, numFactory.numOf(5), true));
        assertNumEquals(105, StopLossRule.stopLossPriceFromDistance(entryPrice, numFactory.numOf(5), false));
    }

    @Test
    public void isSatisfiedWorksForBuy() {
        final var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        final Num tradedAmount = numOf(1);

        // 5% stop-loss
        var rule = new StopLossRule(closePrice, numOf(5));

        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        // Enter at 114
        tradingRecord.enter(2, numOf(114), tradedAmount);
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
        // Exit
        tradingRecord.exit(5);

        // Enter at 128
        tradingRecord.enter(5, numOf(128), tradedAmount);
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertTrue(rule.isSatisfied(6, tradingRecord));
        assertTrue(rule.isSatisfied(7, tradingRecord));
    }

    @Test
    public void isSatisfiedWorksForSell() {
        final var tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL);
        final Num tradedAmount = numOf(1);

        // 5% stop-loss
        var rule = new StopLossRule(closePrice, numOf(5));

        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        // Enter at 108
        tradingRecord.enter(1, numOf(108), tradedAmount);
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(3, tradingRecord));
        // Exit
        tradingRecord.exit(4);

        // Enter at 114
        tradingRecord.enter(2, numOf(114), tradedAmount);
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(3, tradingRecord));
        assertFalse(rule.isSatisfied(4, tradingRecord));
        assertTrue(rule.isSatisfied(5, tradingRecord));
    }

    @Test
    public void worksWithDifferentPriceIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        var highPrice = new HighPriceIndicator(series);
        var rule = new StopLossRule(highPrice, numOf(10));

        var buyRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        var amount = numOf(1);
        buyRecord.enter(3, highPrice.getValue(3), amount);
        assertFalse(rule.isSatisfied(3, buyRecord));
        assertTrue(rule.isSatisfied(2, buyRecord));

        var sellRecord = new BaseTradingRecord(Trade.TradeType.SELL);
        sellRecord.enter(1, highPrice.getValue(1), amount);
        assertFalse(rule.isSatisfied(1, sellRecord));
        assertTrue(rule.isSatisfied(2, sellRecord));
    }

    @Test
    public void serializeAndDeserialize() {
        var rule = new StopLossRule(closePrice, numOf(8));
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(closePrice.getBarSeries(), rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(closePrice.getBarSeries(), rule);
    }
}
