/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;

/**
 * A rule for setting the minimum number of bars up to which an open position
 * should not be closed.
 *
 * <p>
 * Using this rule only makes sense for exit rules. For entry rules,
 * {@link OpenedPositionMinimumBarCountRule#isSatisfied(int, TradingRecord)}
 * always returns {@code false}.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 */
public class OpenedPositionMinimumBarCountRule extends AbstractRule {

    /**
     * The minimum number of bars up to which an open position should not be closed.
     */
    private final int barCount;

    /**
     * Constructor.
     *
     * @param barCount the {@link #barCount}
     */
    public OpenedPositionMinimumBarCountRule(int barCount) {
        if (barCount < 1) {
            throw new IllegalArgumentException("Bar count must be positive");
        }
        this.barCount = barCount;
    }

    /**
     * This rule uses the {@code tradingRecord}.
     *
     * @param index         the bar index
     * @param tradingRecord the required trading history
     * @return true if opened trade has reached {@link #barCount}, otherwise false
     */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        if (tradingRecord != null) {
            Position currentPosition = tradingRecord.getCurrentPosition();
            if (currentPosition != null && currentPosition.isOpened() && currentPosition.getEntry() != null) {
                int entryIndex = currentPosition.getEntry().getIndex();
                int currentBarCount = index - entryIndex;
                satisfied = currentBarCount >= barCount;
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    /** @return the {@link #barCount} */
    public int getBarCount() {
        return barCount;
    }
}
