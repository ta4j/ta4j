/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.NumFactory;

public class TrailingFixedAmountStopGainRuleTest extends AbstractIndicatorTest<Object, Object> {

    public TrailingFixedAmountStopGainRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void isSatisfiedForBuy() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 110, 120, 130, 119.00, 130,
                118.00);

        TrailingFixedAmountStopGainRule rule = new TrailingFixedAmountStopGainRule(closePrice, numOf(10));
        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        tradingRecord.enter(2, numOf(114), numFactory.one());
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
    }

    @Test
    public void doesNotTriggerBeforeGainActivationForBuy() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 108, 96, 94);

        TrailingFixedAmountStopGainRule rule = new TrailingFixedAmountStopGainRule(closePrice, numOf(10));
        tradingRecord.enter(0, numOf(100), numFactory.one());

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void isSatisfiedForSell() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 90, 80, 70, 81.00, 120,
                132.01);

        TrailingFixedAmountStopGainRule rule = new TrailingFixedAmountStopGainRule(closePrice, numOf(10));
        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        tradingRecord.enter(2, numOf(84), numFactory.one());
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
    }

    @Test
    public void doesNotTriggerBeforeGainActivationForSell() {
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 92, 103, 107);

        TrailingFixedAmountStopGainRule rule = new TrailingFixedAmountStopGainRule(closePrice, numOf(10));
        tradingRecord.enter(0, numOf(100), numFactory.one());

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void serializeAndDeserialize() {
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 110, 90, 95, 105);
        TrailingFixedAmountStopGainRule rule = new TrailingFixedAmountStopGainRule(closePrice, numOf(7), 2);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(closePrice.getBarSeries(), rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(closePrice.getBarSeries(), rule);
    }

    @Test
    public void constructorValidation() {
        ClosePriceIndicator closePrice = StopRuleTestSupport.closePrice(numFactory, 100, 101);
        assertThrows(IllegalArgumentException.class,
                () -> new TrailingFixedAmountStopGainRule(null, numFactory.numOf(10), 2));
        assertThrows(IllegalArgumentException.class,
                () -> new TrailingFixedAmountStopGainRule(closePrice, numFactory.zero(), 2));
        assertThrows(IllegalArgumentException.class,
                () -> new TrailingFixedAmountStopGainRule(closePrice, numFactory.minusOne(), 2));
        assertThrows(IllegalArgumentException.class,
                () -> new TrailingFixedAmountStopGainRule(closePrice, numFactory.numOf(10), 0));
    }
}
