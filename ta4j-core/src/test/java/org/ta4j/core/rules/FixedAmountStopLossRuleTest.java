/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

public class FixedAmountStopLossRuleTest extends AbstractIndicatorTest<BarSeries, Num> {

    private ClosePriceIndicator closePrice;
    private TraceTestLogger ruleTraceTestLogger;

    public FixedAmountStopLossRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        closePrice = new ClosePriceIndicator(new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 120, 100, 150, 110, 100)
                .build());
        ruleTraceTestLogger = new TraceTestLogger();
        ruleTraceTestLogger.open();
    }

    @AfterEach
    public void tearDownLogger() {
        ruleTraceTestLogger.close();
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
    public void traceLoggingIncludesStopDecisionFields() {
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        tradingRecord.enter(2, numFactory.numOf(110), numFactory.one());

        var rule = new FixedAmountStopLossRule(closePrice, numFactory.numOf(5));
        ruleTraceTestLogger.clear();

        assertTrue(rule.isSatisfied(4, tradingRecord));

        String logContent = ruleTraceTestLogger.getLogOutput();
        assertTrue(logContent.contains("currentPrice=100"), "Stop trace should include the current price");
        assertTrue(logContent.contains("entryPrice=110"), "Stop trace should include the entry price");
        assertTrue(logContent.contains("stopPrice=105"), "Stop trace should include the stop price");
        assertTrue(logContent.contains("side=BUY"), "Stop trace should include the trade side");
        assertTrue(logContent.contains("lossAmount=5"), "Stop trace should include the fixed loss amount");
        assertFalse(logContent.contains("context={"), "Stop trace should emit flat fields rather than a context map");
    }

    @Test
    public void traceLoggingDoesNotReadPriceAgainWhenLoggerTraceIsDisabled() {
        CountingClosePriceIndicator countingClosePrice = new CountingClosePriceIndicator(closePrice.getBarSeries());
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        tradingRecord.enter(2, numFactory.numOf(110), numFactory.one());
        var rule = new FixedAmountStopLossRule(countingClosePrice, numFactory.numOf(5));

        ruleTraceTestLogger.setLoggerLevel(FixedAmountStopLossRule.class, Level.INFO);
        ruleTraceTestLogger.clear();
        countingClosePrice.reset();

        assertTrue(rule.isSatisfied(4, tradingRecord));

        assertEquals(1, countingClosePrice.valueCallCount(),
                "Disabled TRACE should not perform an extra diagnostic price lookup");
        assertFalse(ruleTraceTestLogger.getLogOutput().contains("FixedAmountStopLossRule#isSatisfied"),
                "Disabled TRACE should not emit stop diagnostics");
    }

    @Test
    public void traceLoggingReportsUnavailableCurrentPrice() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110).build();
        var nanClose = new MockIndicator(series, 0, numFactory.numOf(100), NaN.NaN, numFactory.numOf(110));
        var tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        tradingRecord.enter(0, numFactory.numOf(100), numFactory.one());
        var rule = new FixedAmountStopLossRule(nanClose, numFactory.numOf(5));

        ruleTraceTestLogger.clear();
        assertFalse(rule.isSatisfied(1, tradingRecord));

        assertTrue(ruleTraceTestLogger.getLogOutput().contains("reason=priceUnavailable"),
                "Stop trace should report the unavailable current price");
    }

    @Test
    public void serializeAndDeserialize() {
        var rule = new FixedAmountStopLossRule(closePrice, numFactory.numOf(8));
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(closePrice.getBarSeries(), rule);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(closePrice.getBarSeries(), rule);
    }

    @Test
    public void constructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new FixedAmountStopLossRule(null, numFactory.one()));
        assertThrows(IllegalArgumentException.class, () -> new FixedAmountStopLossRule((Indicator<Num>) null, 1));
        assertThrows(IllegalArgumentException.class, () -> new FixedAmountStopLossRule(closePrice, (Number) null));
        assertThrows(IllegalArgumentException.class, () -> new FixedAmountStopLossRule(closePrice, numFactory.zero()));
        assertThrows(IllegalArgumentException.class,
                () -> new FixedAmountStopLossRule(closePrice, numFactory.minusOne()));
    }

    private static final class CountingClosePriceIndicator extends ClosePriceIndicator {

        private int valueCallCount;

        private CountingClosePriceIndicator(BarSeries series) {
            super(series);
        }

        @Override
        public Num getValue(int index) {
            valueCallCount++;
            return super.getValue(index);
        }

        private void reset() {
            valueCallCount = 0;
        }

        private int valueCallCount() {
            return valueCallCount;
        }
    }
}
