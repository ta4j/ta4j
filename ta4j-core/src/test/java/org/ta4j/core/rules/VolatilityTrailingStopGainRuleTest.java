/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.NumFactory;

public class VolatilityTrailingStopGainRuleTest extends AbstractIndicatorTest<Object, Object> {

    public VolatilityTrailingStopGainRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void isSatisfiedForBuy() {
        var series = StopRuleTestSupport.series(numFactory, 100, 120, 130, 114);
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityTrailingStopGainRule(closePrice, volatility, 1);
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);

        tradingRecord.enter(0, numFactory.hundred(), numFactory.one());

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(3, tradingRecord));

        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
        assertNumEquals(95, rule.stopPrice(series, position));
    }

    @Test
    public void doesNotTriggerBeforeGainActivationForBuy() {
        var series = StopRuleTestSupport.series(numFactory, 100, 103, 97);
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityTrailingStopGainRule(closePrice, volatility, 1);
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);

        tradingRecord.enter(0, numFactory.hundred(), numFactory.one());

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
    }

    @Test
    public void isSatisfiedForBuyAtExactThreshold() {
        var series = StopRuleTestSupport.series(numFactory, 100, 120, 115);
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityTrailingStopGainRule(closePrice, volatility, 1);
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);

        tradingRecord.enter(0, numFactory.hundred(), numFactory.one());

        assertTrue(rule.isSatisfied(2, tradingRecord));
    }

    @Test
    public void isSatisfiedForSell() {
        var series = StopRuleTestSupport.series(numFactory, 100, 90, 80, 86);
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityTrailingStopGainRule(closePrice, volatility, 1);
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);

        tradingRecord.enter(0, numFactory.hundred(), numFactory.one());

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(3, tradingRecord));

        Position position = new Position(Trade.sellAt(0, series), Trade.buyAt(1, series));
        assertNumEquals(105, rule.stopPrice(series, position));
    }

    @Test
    public void doesNotTriggerBeforeGainActivationForSell() {
        var series = StopRuleTestSupport.series(numFactory, 100, 97, 103);
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityTrailingStopGainRule(closePrice, volatility, 1);
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);

        tradingRecord.enter(0, numFactory.hundred(), numFactory.one());

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
    }

    @Test
    public void isSatisfiedForSellAtExactThreshold() {
        var series = StopRuleTestSupport.series(numFactory, 100, 80, 85);
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityTrailingStopGainRule(closePrice, volatility, 1);
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);

        tradingRecord.enter(0, numFactory.hundred(), numFactory.one());

        assertTrue(rule.isSatisfied(2, tradingRecord));
    }

    @Test
    public void serializeAndDeserialize() {
        var series = StopRuleTestSupport.series(numFactory, 100, 90, 80, 86);
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityTrailingStopGainRule(closePrice, volatility, 1, 2);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }

    @Test
    public void constructorValidation() {
        var series = StopRuleTestSupport.series(numFactory, 100, 95, 89);
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        assertThrows(IllegalArgumentException.class,
                () -> new VolatilityTrailingStopGainRule(closePrice, volatility, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new VolatilityTrailingStopGainRule(null, volatility, 2, 2));
    }
}
