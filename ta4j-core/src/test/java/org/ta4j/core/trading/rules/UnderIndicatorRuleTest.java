package org.ta4j.core.trading.rules;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.FixedDecimalIndicator;
import org.ta4j.core.num.Num;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnderIndicatorRuleTest {

    private UnderIndicatorRule rule;
    
    @Before
    public void setUp() {
        TimeSeries series = new BaseTimeSeries();
        Indicator<Num> indicator = new FixedDecimalIndicator(series, 0, 5, 8, 5, 1, 10, 20, 30);
        rule = new UnderIndicatorRule(indicator, series.numOf(5));
    }
    
    @Test
    public void isSatisfied() {
        assertTrue(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
        assertTrue(rule.isSatisfied(4));
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(6));
        assertFalse(rule.isSatisfied(7));
    }
}
        