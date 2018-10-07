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

public class CrossedDownIndicatorRuleTest {

    private CrossedDownIndicatorRule rule;
    
    @Before
    public void setUp() {
        TimeSeries series = new BaseTimeSeries();
        Indicator<Num> evaluatedIndicator = new FixedDecimalIndicator(series, 12, 11, 10, 9, 11, 8, 7, 6);
        rule = new CrossedDownIndicatorRule(evaluatedIndicator, 10);
    }
    
    @Test
    public void isSatisfied() {
        assertFalse(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertTrue(rule.isSatisfied(3));
        assertFalse(rule.isSatisfied(4));
        assertTrue(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(6));
        assertFalse(rule.isSatisfied(7));
    }
}
        