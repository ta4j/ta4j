package org.ta4j.core.trading.rules;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.FixedDecimalIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.helper.ChainLink;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChainRuleTest {

    private ChainRule chainRule;

    @Before
    public void setUp() {
        TimeSeries series = new BaseTimeSeries();
        Indicator<Num> indicator = new FixedDecimalIndicator(series, 6, 5, 8, 5, 1, 10, 20, 30);
        UnderIndicatorRule underIndicatorRule = new UnderIndicatorRule(indicator, series.numOf(5));
        OverIndicatorRule overIndicatorRule = new OverIndicatorRule(indicator, 7);
        chainRule = new ChainRule(new ChainLink(underIndicatorRule ,3), new ChainLink(overIndicatorRule, 3));
    }

    @Test
    public void isSatisfied() {
        assertFalse(chainRule.isSatisfied(0));
        assertTrue(chainRule.isSatisfied(4));
        assertTrue(chainRule.isSatisfied(6));
        assertFalse(chainRule.isSatisfied(7));
    }
}
