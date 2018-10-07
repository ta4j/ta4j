package org.ta4j.core.trading.rules;

import org.ta4j.core.TradingRecord;

import java.util.Arrays;

/**
 * An indexes-based rule.
 * </p>
 * Satisfied for provided indexes.
 */
public class FixedRule extends AbstractRule {

    private final int[] indexes;

    /**
     * Constructor.
     * @param indexes a sequence of indexes
     */
    public FixedRule(int... indexes) {
        this.indexes = Arrays.copyOf(indexes, indexes.length);
    }

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
