package org.ta4j.core.trading.rules;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class StopGainRuleTest extends AbstractIndicatorTest {

    private TradingRecord tradingRecord;
    private ClosePriceIndicator closePrice;

    public StopGainRuleTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        tradingRecord = new BaseTradingRecord();
        closePrice = new ClosePriceIndicator(new MockTimeSeries(numFunction,
                100, 105, 110, 120, 150, 120, 160, 180
        ));
    }
    
    @Test
    public void isSatisfied() {
        final Num tradedAmount = numOf(0);
        
        // 30% stop-gain
        StopGainRule rule = new StopGainRule(closePrice, numOf(30));
        
        assertFalse(rule.isSatisfied(0, null));
        assertFalse(rule.isSatisfied(1, tradingRecord));
        
        // Enter at 108
        tradingRecord.enter(2, numOf(108), tradedAmount);
        assertFalse(rule.isSatisfied(2, tradingRecord));
        assertFalse(rule.isSatisfied(3, tradingRecord));
        assertTrue(rule.isSatisfied(4, tradingRecord));
        // Exit
        tradingRecord.exit(5);
        
        // Enter at 118
        tradingRecord.enter(5, numOf(118), tradedAmount);
        assertFalse(rule.isSatisfied(5, tradingRecord));
        assertTrue(rule.isSatisfied(6, tradingRecord));
        assertTrue(rule.isSatisfied(7, tradingRecord));
    }
}
        