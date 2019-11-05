package org.ta4j.core.trading.rules;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.FixedDecimalIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.helper.ChainLink;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChainRuleTest {

    private ChainRule chainRule;

    @Before
    public void setUp() {
        BarSeries series = new BaseBarSeries();
        Indicator<Num> indicator = new FixedDecimalIndicator(series, 6, 5, 8, 5, 1, 10, 2, 30);
        UnderIndicatorRule underIndicatorRule = new UnderIndicatorRule(indicator, series.numOf(5));
        OverIndicatorRule overIndicatorRule = new OverIndicatorRule(indicator, 7);
        IsEqualRule isEqualRule = new IsEqualRule(indicator, 5);
        chainRule = new ChainRule(underIndicatorRule, new ChainLink(overIndicatorRule, 3), new ChainLink(isEqualRule, 2));
    }

    @Test
    public void isSatisfied() {
        assertFalse(chainRule.isSatisfied(0));
        assertTrue(chainRule.isSatisfied(4));
        assertTrue(chainRule.isSatisfied(6));
        assertFalse(chainRule.isSatisfied(7));
    }
}
