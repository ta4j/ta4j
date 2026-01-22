/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.CrossIndicator;
import org.ta4j.core.num.Num;

/**
 * Satisfied when the value of the first {@link Indicator indicator} crosses-up
 * the value of the second one.
 */
public class CrossedUpIndicatorRule extends AbstractRule {

    /** The cross indicator */
    private final CrossIndicator cross;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param threshold the threshold
     */
    public CrossedUpIndicatorRule(Indicator<Num> indicator, Number threshold) {
        this(indicator, indicator.getBarSeries().numFactory().numOf(threshold));
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param threshold the threshold
     */
    public CrossedUpIndicatorRule(Indicator<Num> indicator, Num threshold) {
        this(indicator, new ConstantIndicator<>(indicator.getBarSeries(), threshold));
    }

    /**
     * Constructor.
     *
     * @param first  the first indicator
     * @param second the second indicator
     */
    public CrossedUpIndicatorRule(Indicator<Num> first, Indicator<Num> second) {
        this.cross = new CrossIndicator(second, first);
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = cross.getValue(index);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    /** @return the initial lower indicator */
    public Indicator<Num> getLow() {
        return cross.getLow();
    }

    /** @return the initial upper indicator */
    public Indicator<Num> getUp() {
        return cross.getUp();
    }
}
