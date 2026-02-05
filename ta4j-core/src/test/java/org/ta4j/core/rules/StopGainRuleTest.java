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
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class StopGainRuleTest extends AbstractIndicatorTest<BarSeries, Num> {

    private ClosePriceIndicator closePrice;

    public StopGainRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        closePrice = new ClosePriceIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 120, 150, 120, 160, 180, 170, 135, 104)
                .build());
    }

    @Test
    public void stopGainPriceCalculatesThresholds() {
        Num entryPrice = numFactory.hundred();
        Num gainPercent = numFactory.numOf(5);

        assertNumEquals(105, StopGainRule.stopGainPrice(entryPrice, gainPercent, true));
        assertNumEquals(95, StopGainRule.stopGainPrice(entryPrice, gainPercent, false));
        assertNumEquals(105, StopGainRule.stopGainPriceFromDistance(entryPrice, numFactory.numOf(5), true));
        assertNumEquals(95, StopGainRule.stopGainPriceFromDistance(entryPrice, numFactory.numOf(5), false));
    }

    @Test
    public void isSatisfiedWorksForBuy() {
        final var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        final Num tradedAmount = numOf(1);

        // 30% stop-gain
        var rule = new StopGainRule(closePrice, numOf(30));

        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        // Enter at 108
        tradingRecord.enter(2, numOf(108), tradedAmount);
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
        // Exit
        tradingRecord.exit(5);

        // Enter at 118
        tradingRecord.enter(5, numOf(118), tradedAmount);
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertTrue(rule.isSatisfied(6, tradingRecord));
        assertTrue(rule.isSatisfied(7, tradingRecord));
    }

    @Test
    public void isSatisfiedWorksForSell() {
        final TradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL);
        final Num tradedAmount = numOf(1);

        // 30% stop-gain
        var rule = new StopGainRule(closePrice, numOf(10));

        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        // Enter at 178
        tradingRecord.enter(7, numOf(178), tradedAmount);
        assertFalse(rule.isSatisfied(7, tradingRecord));
        assertFalse(rule.isSatisfied(8, tradingRecord));
        assertTrue(rule.isSatisfied(9, tradingRecord));
        // Exit
        tradingRecord.exit(10);

        // Enter at 119
        tradingRecord.enter(3, numOf(119), tradedAmount);
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(10, tradingRecord));
    }

    @Test
    public void worksWithDifferentPriceIndicator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        var highPrice = new HighPriceIndicator(series);
        var rule = new StopGainRule(highPrice, numOf(10));

        var buyRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        var amount = numOf(1);
        buyRecord.enter(1, highPrice.getValue(1), amount);
        assertFalse(rule.isSatisfied(1, buyRecord));
        assertTrue(rule.isSatisfied(2, buyRecord));

        var sellRecord = new BaseTradingRecord(Trade.TradeType.SELL);
        sellRecord.enter(3, highPrice.getValue(3), amount);
        assertFalse(rule.isSatisfied(3, sellRecord));
        assertTrue(rule.isSatisfied(2, sellRecord));
    }

    @Test
    public void serializeAndDeserialize() {
        var rule = new StopGainRule(closePrice, numOf(15));
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(closePrice.getBarSeries(), rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(closePrice.getBarSeries(), rule);
    }
}
