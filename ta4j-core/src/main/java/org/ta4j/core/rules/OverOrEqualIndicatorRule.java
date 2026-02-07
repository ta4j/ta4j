/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;

/**
 * A rule that monitors when one {@link Indicator indicator} is over or equal to
 * another.
 *
 * <p>
 * Satisfied when the value of the first {@link Indicator indicator} is greater
 * than or equal to the value of the second one.
 *
 * <p>
 * This rule does not use the {@code tradingRecord}.
 *
 * @since 0.22.2
 */
public class OverOrEqualIndicatorRule extends AbstractRule {

    /** The first indicator. */
    private final Indicator<Num> first;

    /** The second indicator. */
    private final Indicator<Num> second;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param threshold the threshold
     */
    public OverOrEqualIndicatorRule(Indicator<Num> indicator, Number threshold) {
        this(indicator, indicator.getBarSeries().numFactory().numOf(threshold));
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param threshold the threshold
     */
    public OverOrEqualIndicatorRule(Indicator<Num> indicator, Num threshold) {
        this(indicator, new ConstantIndicator<>(indicator.getBarSeries(), threshold));
    }

    /**
     * Constructor.
     *
     * @param first  the first indicator
     * @param second the second indicator
     */
    public OverOrEqualIndicatorRule(Indicator<Num> first, Indicator<Num> second) {
        this.first = first;
        this.second = second;
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = first.getValue(index).isGreaterThanOrEqual(second.getValue(index));
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
