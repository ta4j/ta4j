package org.ta4j.core.trading.rules;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;

public class OverIndicatorWindowRule extends OverIndicatorRule {

    // count: the window of consecutive indices where the rule is applied
    // if 0, the rule is always satisfied
    // if 1, the current index is in the window
    // if 2, the current and the previous indices are in the window
    private int count;
    // percent: the percentage of indices inside the window that must be
    // satisfied
    // if 100, all indices within the window must exceed the threshold
    // if 0, the rule is always satisfied
    private int percent;

    public OverIndicatorWindowRule(Indicator<Decimal> indicator, Decimal threshold, int count) {
        this(indicator, threshold, count, 100);
    }

    public OverIndicatorWindowRule(Indicator<Decimal> indicator, Decimal threshold, int count, int percent) {
        super(indicator, threshold);
        this.count = count;
        this.percent = percent;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (count == 0 || percent == 0) {
            return true;
        }
        int satCount = 0;
        for (int i = 0; i < count; i++) {
            if (super.isSatisfied(index - i, tradingRecord)) {
                satCount++;
            }
        }
        return (((double) satCount / (double) count) * 100) >= percent;
    }

}
