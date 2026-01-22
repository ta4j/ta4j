/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;

/**
 * Satisfied when the value of the first {@link Indicator indicator} is equal to
 * the value of the second one.
 *
 * <p>
 * This rule does not use the {@code tradingRecord}.
 */
public class IsEqualRule extends AbstractRule {

    /** The first indicator. */
    private final Indicator<Num> first;

    /** The second indicator. */
    private final Indicator<Num> second;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param value     the value to check
     */
    public IsEqualRule(Indicator<Num> indicator, Number value) {
        this(indicator, indicator.getBarSeries().numFactory().numOf(value));
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param value     the value to check
     */
    public IsEqualRule(Indicator<Num> indicator, Num value) {
        this(indicator, new ConstantIndicator<>(indicator.getBarSeries(), value));
    }

    /**
     * Constructor.
     *
     * @param first  the first indicator
     * @param second the second indicator
     */
    public IsEqualRule(Indicator<Num> first, Indicator<Num> second) {
        this.first = first;
        this.second = second;
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = first.getValue(index).isEqual(second.getValue(index));
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
