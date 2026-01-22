/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;

/**
 * Satisfied when the value of the {@link Indicator indicator} is between two
 * other indicators or values.
 *
 * <p>
 * This rule does not use the {@code tradingRecord}.
 */
public class InPipeRule extends AbstractRule {

    /** The upper indicator */
    private final Indicator<Num> upper;

    /** The lower indicator */
    private final Indicator<Num> lower;

    /** The evaluated indicator */
    private final Indicator<Num> ref;

    /**
     * Constructor.
     *
     * @param ref   the reference indicator
     * @param upper the upper threshold
     * @param lower the lower threshold
     */
    public InPipeRule(Indicator<Num> ref, Number upper, Number lower) {
        this(ref, ref.getBarSeries().numFactory().numOf(upper), ref.getBarSeries().numFactory().numOf(lower));
    }

    /**
     * Constructor.
     *
     * @param ref   the reference indicator
     * @param upper the upper threshold
     * @param lower the lower threshold
     */
    public InPipeRule(Indicator<Num> ref, Num upper, Num lower) {
        this(ref, new ConstantIndicator<>(ref.getBarSeries(), upper),
                new ConstantIndicator<>(ref.getBarSeries(), lower));
    }

    /**
     * Constructor.
     *
     * @param ref   the reference indicator
     * @param upper the upper indicator
     * @param lower the lower indicator
     */
    public InPipeRule(Indicator<Num> ref, Indicator<Num> upper, Indicator<Num> lower) {
        this.upper = upper;
        this.lower = lower;
        this.ref = ref;
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        Num refValue = ref.getValue(index);
        final boolean satisfied = refValue.isLessThanOrEqual(upper.getValue(index))
                && refValue.isGreaterThanOrEqual(lower.getValue(index));
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
