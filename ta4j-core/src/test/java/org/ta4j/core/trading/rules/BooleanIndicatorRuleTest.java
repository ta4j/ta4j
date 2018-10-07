package org.ta4j.core.trading.rules;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BooleanIndicatorRuleTest {

    private BooleanIndicatorRule rule;
    
    @Before
    public void setUp() {
        Indicator<Boolean> indicator = new FixedIndicator<Boolean>(new BaseTimeSeries(),true, true, false, false, true);
        rule = new BooleanIndicatorRule(indicator);
    }
    
    @Test
    public void isSatisfied() {
        assertTrue(rule.isSatisfied(0));
        assertTrue(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
        assertTrue(rule.isSatisfied(4));
    }
}
        