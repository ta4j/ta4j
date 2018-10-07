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

public class IsFallingRuleTest {

    private IsFallingRule rule;

	@Before
	public void setUp() {
        TimeSeries series = new BaseTimeSeries();
		Indicator<Num> indicator = new FixedDecimalIndicator(series, 6, 5, 4, 3, 2, 1, 0, -1, 2, 3);
		rule = new IsFallingRule(indicator, 3);
	}

	@Test
	public void isSatisfied() {
		assertFalse(rule.isSatisfied(0));
		assertFalse(rule.isSatisfied(1));
		assertFalse(rule.isSatisfied(2));
		assertTrue(rule.isSatisfied(3));
		assertTrue(rule.isSatisfied(4));
		assertTrue(rule.isSatisfied(5));
		assertTrue(rule.isSatisfied(6));
		assertTrue(rule.isSatisfied(7));
		assertFalse(rule.isSatisfied(8));
		assertFalse(rule.isSatisfied(9));
	}
}
