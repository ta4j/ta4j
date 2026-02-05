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

public class VolatilityStopGainRuleTest extends AbstractIndicatorTest<Object, Object> {

    public VolatilityStopGainRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void isSatisfiedForBuy() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 111).build();
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
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 89).build();
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
    public void serializeAndDeserialize() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 89).build();
        var closePrice = new ClosePriceIndicator(series);
        var volatility = new ConstantIndicator<>(series, numFactory.numOf(5));
        var rule = new VolatilityStopGainRule(closePrice, volatility, 2);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rule);
    }
}
