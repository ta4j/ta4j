/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.Indicator;

public class VolatilityStopGainRuleTest extends AbstractIndicatorTest<BarSeries, Num> {

    public VolatilityStopGainRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void isSatisfiedForBuy() {
        var series = StopRuleTestSupport.series(numFactory, 100, 105, 111);
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityStopGainRule(closePrice, volatility, 2);
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);

        tradingRecord.enter(0, numFactory.hundred(), numFactory.one());

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(2, tradingRecord));

        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
        assertNumEquals(110, rule.stopPrice(series, position));
    }

    @Test
    public void isSatisfiedForSell() {
        var series = StopRuleTestSupport.series(numFactory, 100, 95, 89);
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityStopGainRule(closePrice, volatility, 2);
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);

        tradingRecord.enter(0, numFactory.hundred(), numFactory.one());

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertTrue(rule.isSatisfied(2, tradingRecord));

        Position position = new Position(Trade.sellAt(0, series), Trade.buyAt(1, series));
        assertNumEquals(90, rule.stopPrice(series, position));
    }

    @Test
    public void isSatisfiedForBuyAtExactThreshold() {
        var series = StopRuleTestSupport.series(numFactory, 100, 110);
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityStopGainRule(closePrice, volatility, 2);
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);

        tradingRecord.enter(0, numFactory.hundred(), numFactory.one());

        assertTrue(rule.isSatisfied(1, tradingRecord));
    }

    @Test
    public void isSatisfiedForSellAtExactThreshold() {
        var series = StopRuleTestSupport.series(numFactory, 100, 90);
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityStopGainRule(closePrice, volatility, 2);
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);

        tradingRecord.enter(0, numFactory.hundred(), numFactory.one());

        assertTrue(rule.isSatisfied(1, tradingRecord));
    }

    @Test
    public void serializeAndDeserialize() {
        var series = StopRuleTestSupport.series(numFactory, 100, 95, 89);
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityStopGainRule(closePrice, volatility, 2);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }

    @Test
    public void constructorValidation() {
        var series = StopRuleTestSupport.series(numFactory, 100, 95, 89);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var closePrice = new ClosePriceIndicator(series);
        assertThrows(IllegalArgumentException.class,
                () -> new VolatilityStopGainRule((Indicator<Num>) null, volatility, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new VolatilityStopGainRule(closePrice, (Indicator<Num>) null, 2));
        assertThrows(IllegalArgumentException.class, () -> new VolatilityStopGainRule(closePrice, volatility, 0));
        assertThrows(IllegalArgumentException.class, () -> new VolatilityStopGainRule(closePrice, volatility, -1));
    }
}
