package org.ta4j.core.rules;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockRule;
import org.ta4j.core.mocks.MockRuleWithCtx;

public class AbstractRuleTest {

	@Test
	public void test_that_convenient_is_satisfied_works_for_both_interfaces() {
		AbstractRule testee = new AbstractRule() {

			@Override
			public boolean isSatisfied(int index, TradingRecord tradingRecord) {
				return false;
			}
			
		};
		MockRule rule = new MockRule(true);
		MockRuleWithCtx ruleCtx = new MockRuleWithCtx(true);
		TradingRecord tr = new BaseTradingRecord();
		// test Rule
		testee.isSatisfied(rule, 0, tr, null);
		assertEquals(1, rule.getCalls().size());
		assertEquals(Integer.valueOf(0), rule.getCalls().get(0));
		
		// test RuleWithCtx
		testee.isSatisfied(ruleCtx, 1, tr, null);
		assertEquals(1, ruleCtx.getCallsWithCtx().size());
		assertEquals(0, ruleCtx.getCalls().size());
		assertEquals(Integer.valueOf(1), ruleCtx.getCallsWithCtx().get(0));
		
	}

}
