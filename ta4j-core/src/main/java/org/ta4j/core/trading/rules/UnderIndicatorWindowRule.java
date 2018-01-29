package org.ta4j.core.trading.rules;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;

public class UnderIndicatorWindowRule extends UnderIndicatorRule {

	// count: the window of consecutive indices where the rule is applied
	// if 0, the rule is always satisfied
	// if 1, the current index is in the window
	// if 2, the current and the previous indices are in the window
	private int count;
	// percent: the percentage of indices inside the window that must be satisfied
	// if 100, all indices within the window must exceed the threshold
	// if 0, the rule is always satisfied
	private int percent;
	
	public UnderIndicatorWindowRule(Indicator<Decimal> indicator, Decimal threshold, int count) {
		this(indicator, threshold, count, 100);
	}
	
	public UnderIndicatorWindowRule(Indicator<Decimal> indicator, Decimal threshold, int count, int percent) {
		super(indicator, threshold);
		this.count = count;
		this.percent = percent;
	}
	
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
    	int satCount = 0;
    	for (int i = 0; i < count; i++) {
    		if (super.isSatisfied(index - i, tradingRecord)) {
    			satCount++;
    		}
    	}
    	//log.trace("UICR: " + index + " " + super.first.getTimeSeries().getTick(index).getSimpleDateName() + " true");
    	return (((double) satCount / (double) count) * 100) >= percent;
    }

}
