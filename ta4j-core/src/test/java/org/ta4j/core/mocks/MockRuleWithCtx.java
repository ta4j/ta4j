package org.ta4j.core.mocks;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.RuleWithCtx;
import org.ta4j.core.StrategyContext;
import org.ta4j.core.TradingRecord;

/**
 * mock for testing RuleWithCtx
 *
 */
public class MockRuleWithCtx extends MockRule implements RuleWithCtx {

	protected List<Integer> callsWithCtx = new ArrayList<>();
	
	/**
	 * Last result is taken if index is greater than size of the given results.
	 * @param results result per index
	 */
	public MockRuleWithCtx(boolean... results) {
		super(results);
	} 
	
	@Override
	public boolean isSatisfied(int index, TradingRecord tradingRecord, StrategyContext ctx) {
		callsWithCtx.add(index);
		if (results.length > index) {
			return results[index];
		}
		return results[results.length - 1];
	}
	
	
	public List<Integer> getCallsWithCtx() {
		return callsWithCtx;
	}
}
