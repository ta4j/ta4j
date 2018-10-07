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

public class IsEqualRuleTest {

    private IsEqualRule rule;
    
    @Before
    public void setUp() {
        TimeSeries series = new BaseTimeSeries();
        Indicator<Num> indicator = new FixedDecimalIndicator(series, 20, 10, 0, -20);
        rule = new IsEqualRule(indicator, series.numOf(20));
    }
    
    @Test
    public void isSatisfied() {
        assertTrue(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
    }
}
