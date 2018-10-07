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

public class IsHighestRuleTest {

    private IsHighestRule rule;

	@Before
	public void setUp() {
		TimeSeries series = new BaseTimeSeries();
        Indicator<Num> indicator = new FixedDecimalIndicator(series, 1, 5, 3, 6, 5, 7, 0, -1, 2, 3);
		rule = new IsHighestRule(indicator, 3);
	}

	@Test
	public void isSatisfied() {
		assertTrue(rule.isSatisfied(0));
		assertTrue(rule.isSatisfied(1));
		assertFalse(rule.isSatisfied(2));
		assertTrue(rule.isSatisfied(3));
		assertFalse(rule.isSatisfied(4));
		assertTrue(rule.isSatisfied(5));
		assertFalse(rule.isSatisfied(6));
		assertFalse(rule.isSatisfied(7));
		assertTrue(rule.isSatisfied(8));
		assertTrue(rule.isSatisfied(9));
	}
}
