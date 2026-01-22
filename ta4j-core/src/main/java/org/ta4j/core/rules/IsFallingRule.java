/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Satisfied when the values of the {@link Indicator indicator} decrease within
 * the {@code barCount}.
 *
 * <p>
 * This rule does not use the {@code tradingRecord}.
 */
public class IsFallingRule extends AbstractRule {

    /** The actual indicator */
    private final Indicator<Num> ref;

    /** The barCount */
    private final int barCount;

    /** The minimum required strength of the falling */
    private final double minStrength;

    /**
     * Constructor.
     *
     * @param ref      the indicator
     * @param barCount the time frame
     */
    public IsFallingRule(Indicator<Num> ref, int barCount) {
        this(ref, barCount, 1.0);
    }

    /**
     * Constructor.
     *
     * @param ref         the indicator
     * @param barCount    the time frame
     * @param minStrenght the minimum required falling strength (between '0' and
     *                    '1', e.g. '1' for strict falling)
     */
    public IsFallingRule(Indicator<Num> ref, int barCount, double minStrenght) {
        this.ref = ref;
        this.barCount = barCount;
        this.minStrength = minStrenght >= 1 ? 0.99 : minStrenght;
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {

        int count = 0;
        for (int i = Math.max(0, index - barCount + 1); i <= index; i++) {
            if (ref.getValue(i).isLessThan(ref.getValue(Math.max(0, i - 1)))) {
                count += 1;
            }
        }

        double ratio = count / (double) barCount;

        final boolean satisfied = ratio >= minStrength;
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
