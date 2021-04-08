package org.ta4j.core.mocks;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

public class MockRule implements Rule {
	
	protected boolean[] results;
	private List<Integer> calls = new ArrayList<>();

	/**
	 * Last result is taken if index is greater than size of the given results.
	 * @param results result per index
	 */
	public MockRule(boolean... results) {
		if (results == null) {
			throw new IllegalArgumentException("results must not be empty");
		}
		this.results = results;
	} 

	@Override
	public boolean isSatisfied(int index, TradingRecord tradingRecord) {
		calls.add(index);
		if (results.length > index) {
			return results[index];
		}
		return results[results.length - 1];
	}
	
	public List<Integer> getCalls() {
		return calls;
	}
	

}
