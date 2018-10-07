package org.ta4j.core.trading.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;

/**
 * Indicator-under-indicator rule.
 * </p>
 * Satisfied when the value of the first {@link Indicator indicator} is strictly lesser than the value of the second one.
 */
public class UnderIndicatorRule extends AbstractRule {

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
    public UnderIndicatorRule(Indicator<Num> indicator, Number threshold) {
        this(indicator, new ConstantIndicator<>(indicator.getTimeSeries(), indicator.numOf(threshold)));
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param threshold a threshold
     */
    public UnderIndicatorRule(Indicator<Num> indicator, Num threshold) {
        this(indicator, new ConstantIndicator<>(indicator.getTimeSeries(), threshold));
    }

    /**
     * Constructor.
     *
     * @param first  the first indicator
     * @param second the second indicator
     */
    public UnderIndicatorRule(Indicator<Num> first, Indicator<Num> second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = first.getValue(index).isLessThan(second.getValue(index));
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
