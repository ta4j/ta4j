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
 * Satisfied when the value of the first {@link Indicator indicator}
 * crosses-down the value of the second one.
 */
public class CrossedDownIndicatorRule extends AbstractRule {

    /** The cross indicator */
    /**
     * The operands in constructor order. Serialization reads rule fields, and the
     * cross indicator alone exposes them as {@code low}/{@code up}, which for a
     * cross-down is the reverse of the constructor order.
     */
    private final Indicator<Num> first;
    private final Indicator<Num> second;
    private final CrossIndicator cross;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param threshold a threshold
     */
    public CrossedDownIndicatorRule(Indicator<Num> indicator, Number threshold) {
        this(indicator, indicator.getBarSeries().numFactory().numOf(threshold));
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param threshold a threshold
     */
    public CrossedDownIndicatorRule(Indicator<Num> indicator, Num threshold) {
        this(indicator, new ConstantIndicator<>(indicator.getBarSeries(), threshold));
    }

    /**
     * Constructor.
     *
     * @param first  the first indicator
     * @param second the second indicator
     */
    public CrossedDownIndicatorRule(Indicator<Num> first, Indicator<Num> second) {
        this.first = first;
        this.second = second;
        this.cross = new CrossIndicator(first, second);
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = cross.getValue(index);
        if (isTraceEnabled()) {
            traceIsSatisfied(index, satisfied,
                    traceCrossContext(cross.getUp(), cross.getLow(), index, satisfied, false));
        }
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
