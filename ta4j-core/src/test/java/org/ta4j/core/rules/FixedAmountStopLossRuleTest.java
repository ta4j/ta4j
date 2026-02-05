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
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class FixedAmountStopLossRuleTest extends AbstractIndicatorTest<BarSeries, Num> {

    private ClosePriceIndicator closePrice;

    public FixedAmountStopLossRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        closePrice = new ClosePriceIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 120, 100, 150, 110, 100)
                .build());
    }

    @Test
    public void isSatisfiedWorksForBuy() {
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        Num tradedAmount = numFactory.one();

        var rule = new FixedAmountStopLossRule(closePrice, numFactory.numOf(5));

        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        tradingRecord.enter(2, numFactory.numOf(110), tradedAmount);
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
    }

    @Test
    public void isSatisfiedWorksForSell() {
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL);
        Num tradedAmount = numFactory.one();

        var rule = new FixedAmountStopLossRule(closePrice, numFactory.numOf(5));

        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        tradingRecord.enter(1, numFactory.numOf(108), tradedAmount);
        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void stopPriceUsesEntryPrice() {
        var series = closePrice.getBarSeries();
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        var rule = new FixedAmountStopLossRule(closePrice, numFactory.numOf(5));

        assertNumEquals(95, rule.stopPrice(series, position));
    }

    @Test
    public void serializeAndDeserialize() {
        var rule = new FixedAmountStopLossRule(closePrice, numFactory.numOf(8));
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(closePrice.getBarSeries(), rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(closePrice.getBarSeries(), rule);
    }
}
