package org.ta4j.core.rules;

import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FixedPriceStopGainRuleTest extends AbstractIndicatorTest<BarSeries, Num> {

    public FixedPriceStopGainRuleTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void testWithNullTradingRecord(){
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(null);

        FixedPriceStopGainRule rule = new FixedPriceStopGainRule(closePriceIndicator);

        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(1, null));
    }

    @Test
    public void testIsSatisfiedNoPositionOpened(){
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(null);

        final TradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL);
        FixedPriceStopGainRule rule = new FixedPriceStopGainRule(closePriceIndicator);
        assertFalse(rule.isSatisfied(1, tradingRecord));
    }


    @Test
    public void isSatisfiedWorksForBuy() {
        ClosePriceIndicator closePriceIndicator =
                new ClosePriceIndicator(
                        new MockBarSeries(numFunction,
                                48873.0, 48872.5, 48799.0, 48765.5, 48709.5, 49006.0, 49587.5, 48674.5, 48782.0, 48750.0));

        final TradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.BUY);
        FixedPriceStopGainRule rule = new FixedPriceStopGainRule(closePriceIndicator);
        final Num tradedAmount = numOf(0.01);

        assertFalse(rule.isSatisfied(0, null));

        tradingRecord.enter(1, numOf(48872.0), tradedAmount);
        tradingRecord.getCurrentPosition().setCustomPositionData(new CustomPositionData(null, numOf(49550.0)));

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertFalse(rule.isSatisfied(4, tradingRecord));
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertTrue(rule.isSatisfied(6, tradingRecord));
    }

    @Test
    public void isSatisfiedWorksForSell() {
        ClosePriceIndicator closePriceIndicator =
                new ClosePriceIndicator(
                        new MockBarSeries(numFunction,
                                48873.0, 48872.5, 48799.0, 48765.5, 48709.5, 49006.0, 49587.5, 48674.5, 48782.0, 48750.0));

        final TradingRecord tradingRecord = new BaseTradingRecord(Trade.TradeType.SELL);
        FixedPriceStopGainRule rule = new FixedPriceStopGainRule(closePriceIndicator);
        final Num tradedAmount = numOf(0.01);

        assertFalse(rule.isSatisfied(0, null));

        tradingRecord.enter(1, numOf(48872.0), tradedAmount);
        tradingRecord.getCurrentPosition().setCustomPositionData(new CustomPositionData(null, numOf(48705.0)));

        assertFalse(rule.isSatisfied(1, tradingRecord));
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertFalse(rule.isSatisfied(4, tradingRecord));
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertFalse(rule.isSatisfied(6, tradingRecord));
        assertTrue(rule.isSatisfied(7, tradingRecord));
    }
}
