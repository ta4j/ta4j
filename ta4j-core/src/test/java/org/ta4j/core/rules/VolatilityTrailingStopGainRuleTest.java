/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class VolatilityTrailingStopGainRuleTest extends AbstractIndicatorTest<Object, Object> {

    public VolatilityTrailingStopGainRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void isSatisfiedForBuy() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 130, 114).build();
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
    public void isSatisfiedForSell() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 90, 80, 86).build();
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
    public void serializeAndDeserialize() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 90, 80, 86).build();
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityTrailingStopGainRule(closePrice, volatility, 1, 2);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
