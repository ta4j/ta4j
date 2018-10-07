package org.ta4j.core.trading.rules;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.FixedDecimalIndicator;
import org.ta4j.core.num.Num;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CrossedUpIndicatorRuleTest {

    private CrossedUpIndicatorRule rule;
    
    @Before
    public void setUp() {
        Indicator<Num> evaluatedIndicator = new FixedDecimalIndicator(new BaseTimeSeries(),8d, 9d, 10d, 12d, 9d, 11d, 12d, 13d);
        rule = new CrossedUpIndicatorRule(evaluatedIndicator, 10);
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
        