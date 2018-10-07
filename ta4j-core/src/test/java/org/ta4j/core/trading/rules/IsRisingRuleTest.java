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

public class IsRisingRuleTest {

    private IsRisingRule rule;

	@Before
	public void setUp() {
		TimeSeries series = new BaseTimeSeries();
        Indicator<Num> indicator = new FixedDecimalIndicator(series, 1, 2, 3, 4, 5, 6, 0, 1, 2, 3);
		rule = new IsRisingRule(indicator, 3);
	}

	@Test
	public void isSatisfied() {
		assertFalse(rule.isSatisfied(0));
		assertFalse(rule.isSatisfied(1));
		assertFalse(rule.isSatisfied(2));
		// First time to have at least 3 rising values.
		assertTrue(rule.isSatisfied(3));
		assertTrue(rule.isSatisfied(4));
		assertTrue(rule.isSatisfied(5));
		assertFalse(rule.isSatisfied(6));
		assertFalse(rule.isSatisfied(7));
		assertFalse(rule.isSatisfied(8));
		assertTrue(rule.isSatisfied(9));
	}
}
