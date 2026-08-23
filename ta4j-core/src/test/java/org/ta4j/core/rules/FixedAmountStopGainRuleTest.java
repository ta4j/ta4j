/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.NaN;

public class FixedAmountStopGainRuleTest extends AbstractIndicatorTest<BarSeries, Num> {

    private ClosePriceIndicator closePrice;

    private TraceTestLogger ruleTraceTestLogger;

    public FixedAmountStopGainRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        closePrice = new ClosePriceIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 120, 100, 150, 110, 100)
                .build());
        ruleTraceTestLogger = new TraceTestLogger();
        ruleTraceTestLogger.open();
    }

    @After
    public void tearDownLogger() {
        ruleTraceTestLogger.close();
    }

    @Test
    public void isSatisfiedWorksForBuy() {
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        Num tradedAmount = numFactory.one();

        var rule = new FixedAmountStopGainRule(closePrice, numFactory.numOf(5));

        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        tradingRecord.enter(2, numFactory.numOf(110), tradedAmount);
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertTrue(rule.isSatisfied(3, tradingRecord));
    }

    @Test
    public void isSatisfiedWorksForSell() {
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL);
        Num tradedAmount = numFactory.one();

        var rule = new FixedAmountStopGainRule(closePrice, numFactory.numOf(5));

        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));

        tradingRecord.enter(3, numFactory.numOf(120), tradedAmount);
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
    }

    @Test
    public void stopPriceUsesEntryPrice() {
        var series = closePrice.getBarSeries();
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        var rule = new FixedAmountStopGainRule(closePrice, numFactory.numOf(5));

        assertNumEquals(105, rule.stopPrice(series, position));
    }

    @Test
    public void serializeAndDeserialize() {
        var rule = new FixedAmountStopGainRule(closePrice, numFactory.numOf(8));
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(closePrice.getBarSeries(), rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(closePrice.getBarSeries(), rule);
    }

    @Test
    public void constructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new FixedAmountStopGainRule(null, numFactory.one()));
        assertThrows(IllegalArgumentException.class, () -> new FixedAmountStopGainRule((Indicator<Num>) null, 1));
        assertThrows(IllegalArgumentException.class, () -> new FixedAmountStopGainRule(closePrice, (Number) null));
        assertThrows(IllegalArgumentException.class, () -> new FixedAmountStopGainRule(closePrice, numFactory.zero()));
        assertThrows(IllegalArgumentException.class,
                () -> new FixedAmountStopGainRule(closePrice, numFactory.minusOne()));
    }

    @Test
    public void traceLoggingReportsUnavailableCurrentPrice() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110).build();
        var nanClose = new MockIndicator(series, 0, numFactory.numOf(100), NaN.NaN, numFactory.numOf(110));
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        tradingRecord.enter(0, numFactory.numOf(100), numFactory.one());
        var rule = new FixedAmountStopGainRule(nanClose, numFactory.numOf(5));

        ruleTraceTestLogger.clear();
        assertFalse(rule.isSatisfied(1, tradingRecord));

        assertTrue("Stop trace should report the unavailable current price",
                ruleTraceTestLogger.getLogOutput().contains("reason=priceUnavailable"));
    }
}
