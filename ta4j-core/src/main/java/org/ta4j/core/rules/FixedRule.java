/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Arrays;

import org.ta4j.core.TradingRecord;

/**
 * Satisfied when any of the specified {@code indexes} match the current bar
 * index.
 *
 * <p>
 * This rule does not use the {@code tradingRecord}.
 */
public class FixedRule extends AbstractRule {

    private final int[] indexes;

    /**
     * Constructor.
     *
     * @param indexes a sequence of indices to be compared to the current bar index
     */
    public FixedRule(int... indexes) {
        this.indexes = Arrays.copyOf(indexes, indexes.length);
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        for (int idx : indexes) {
            if (idx == index) {
                satisfied = true;
                break;
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
