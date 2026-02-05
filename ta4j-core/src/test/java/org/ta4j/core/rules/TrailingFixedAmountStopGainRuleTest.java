/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class TrailingFixedAmountStopGainRuleTest extends AbstractIndicatorTest<Object, Object> {

    public TrailingFixedAmountStopGainRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void isSatisfiedForBuy() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 120, 130, 119.00, 130, 118.00)
                .build());

        TrailingFixedAmountStopGainRule rule = new TrailingFixedAmountStopGainRule(closePrice, numOf(10));
        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        tradingRecord.enter(2, numOf(114), numFactory.one());
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
    }

    @Test
    public void isSatisfiedForSell() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 90, 80, 70, 81.00, 120, 132.01)
                .build());

        TrailingFixedAmountStopGainRule rule = new TrailingFixedAmountStopGainRule(closePrice, numOf(10));
        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        tradingRecord.enter(2, numOf(84), numFactory.one());
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
    }

    @Test
    public void serializeAndDeserialize() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(
                new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 90, 95, 105).build());
        TrailingFixedAmountStopGainRule rule = new TrailingFixedAmountStopGainRule(closePrice, numOf(7), 2);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(closePrice.getBarSeries(), rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(closePrice.getBarSeries(), rule);
    }
}
