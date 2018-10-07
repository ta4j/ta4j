package org.ta4j.core.trading.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Indicator-highest-indicator rule.
 * </p>
 * Satisfied when the value of the {@link Indicator indicator} is the highest
 * within the barCount.
 */
public class IsHighestRule extends AbstractRule {

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
    public IsHighestRule(Indicator<Num> ref, int barCount) {
        this.ref = ref;
        this.barCount = barCount;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        HighestValueIndicator highest = new HighestValueIndicator(ref, barCount);
        Num highestVal = highest.getValue(index);
        Num refVal = ref.getValue(index);

        final boolean satisfied = !refVal.isNaN() && !highestVal.isNaN() && refVal.equals(highestVal);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
