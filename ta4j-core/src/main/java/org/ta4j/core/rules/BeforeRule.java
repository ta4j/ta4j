package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

/**
 * Satisfied when the first rule is satisfied before the second (i.e. both rules
 * must be satisfied). Between these two conditions, the specified reset rule is
 * not allowed to hold.
 */
public class BeforeRule extends AbstractRule {

    private final BarSeries series;
    private final Rule firstCondition;
    private final Rule secondCondition;
    private final Rule resetCondition;

    public BeforeRule(BarSeries series, Rule firstCondition, Rule secondCondition, Rule resetCondition) {
        super();
        this.series = series;
        this.firstCondition = firstCondition;
        this.secondCondition = secondCondition;
        this.resetCondition = resetCondition;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (!secondCondition.isSatisfied(index, tradingRecord)) {
            traceIsSatisfied(index, false);
            return false;
        }

        for (int i = index; i >= series.getBeginIndex(); i--) {
            if (resetCondition.isSatisfied(i, tradingRecord)) {
                traceIsSatisfied(index, false);
                return false;
            }
            if (firstCondition.isSatisfied(i, tradingRecord)) {
                traceIsSatisfied(index, true);
                return true;
            }
        }

        traceIsSatisfied(index, false);
        return false;
    }
}
