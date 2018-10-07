package org.ta4j.core.trading.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;

/**
 * A boolean-indicator-based rule.
 * </p>
 * Satisfied when the value of the {@link Indicator indicator} is true.
 */
public class BooleanIndicatorRule extends AbstractRule {

    private final Indicator<Boolean> indicator;

    /**
     * Constructor.
     *
     * @param indicator a boolean indicator
     */
    public BooleanIndicatorRule(Indicator<Boolean> indicator) {
        this.indicator = indicator;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = indicator.getValue(index);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
