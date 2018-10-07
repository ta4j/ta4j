package org.ta4j.core.trading.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.CrossIndicator;
import org.ta4j.core.num.Num;

/**
 * Crossed-up indicator rule.
 * </p>
 * Satisfied when the value of the first {@link Indicator indicator} crosses-up the value of the second one.
 */
public class CrossedUpIndicatorRule extends AbstractRule {

    /** The cross indicator */
    private CrossIndicator cross;

    /**
     * Constructor.
     * @param indicator the indicator
     * @param threshold a threshold
     */
    public CrossedUpIndicatorRule(Indicator<Num> indicator, Number threshold) {
        this(indicator, indicator.numOf(threshold));
    }

    /**
     * Constructor.
     * @param indicator the indicator
     * @param threshold a threshold
     */
    public CrossedUpIndicatorRule(Indicator<Num> indicator, Num threshold) {
        this(indicator, new ConstantIndicator<Num>(indicator.getTimeSeries(),threshold));
    }


    /**
     * Constructor.
     * @param first the first indicator
     * @param second the second indicator
     */
    public CrossedUpIndicatorRule(Indicator<Num> first, Indicator<Num> second) {
        this.cross = new CrossIndicator(second, first);
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = cross.getValue(index);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
