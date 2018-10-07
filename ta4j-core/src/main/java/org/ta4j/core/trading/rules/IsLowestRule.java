package org.ta4j.core.trading.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Indicator-lowest-indicator rule.
 * </p>
 * Satisfied when the value of the {@link Indicator indicator} is the lowest
 * within the barCount.
 */
public class IsLowestRule extends AbstractRule {

    /**
     * The actual indicator
     */
    private final Indicator<Num> ref;
    /**
     * The barCount
     */
    private final int barCount;

    /**
     * Constructor.
     *
     * @param ref      the indicator
     * @param barCount the time frame
     */
    public IsLowestRule(Indicator<Num> ref, int barCount) {
        this.ref = ref;
        this.barCount = barCount;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        LowestValueIndicator lowest = new LowestValueIndicator(ref, barCount);
        Num lowestVal = lowest.getValue(index);
        Num refVal = ref.getValue(index);

        final boolean satisfied = !refVal.isNaN() && !lowestVal.isNaN() && refVal.equals(lowestVal);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
