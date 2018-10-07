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

    public class InPipeRuleTest {

    private InPipeRule rule;
    
    @Before
    public void setUp() {
        TimeSeries series = new BaseTimeSeries("I am empty");
        Indicator<Num> indicator = new FixedDecimalIndicator(series, 50d, 70d, 80d, 90d, 99d, 60d, 30d, 20d, 10d, 0d);
        rule = new InPipeRule(indicator, series.numOf(80), series.numOf(20));
    }
    
    @Test
    public void isSatisfied() {
        assertTrue(rule.isSatisfied(0));
        assertTrue(rule.isSatisfied(1));
        assertTrue(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
        assertFalse(rule.isSatisfied(4));
        assertTrue(rule.isSatisfied(5));
        assertTrue(rule.isSatisfied(6));
        assertTrue(rule.isSatisfied(7));
        assertFalse(rule.isSatisfied(8));
        assertFalse(rule.isSatisfied(9));
    }
}
        