package org.ta4j.core.trading.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;

/**
 * Indicator-over-indicator rule.
 * </p>
 * Satisfied when the value of the first {@link Indicator indicator} is strictly greater than the value of the second one.
 */
public class OverIndicatorRule extends AbstractRule {

    /**
     * The first indicator
     */
    private final Indicator<Num> first;
    /**
     * The second indicator
     */
    private final Indicator<Num> second;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param threshold a threshold
     */
    public OverIndicatorRule(Indicator<Num> indicator, Number threshold) {
        this(indicator, indicator.numOf(threshold));
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param threshold a threshold
     */
    public OverIndicatorRule(Indicator<Num> indicator, Num threshold) {
        this(indicator, new ConstantIndicator<Num>(indicator.getTimeSeries(), threshold));
    }

    /**
     * Constructor.
     *
     * @param first  the first indicator
     * @param second the second indicator
     */
    public OverIndicatorRule(Indicator<Num> first, Indicator<Num> second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = first.getValue(index).isGreaterThan(second.getValue(index));
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
