/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Number of bars criterion.
 *
 * <p>
 * Returns the total number of bars in all the closed positions. For a trading
 * record only positions closed by the record's logical end are counted; later
 * exits are still open at that end.
 */
public class NumberOfBarsCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position.isClosed()) {
            final int exitIndex = position.getExit().getIndex();
            final int entryIndex = position.getEntry().getIndex();
            return series.numFactory().numOf(exitIndex - entryIndex + 1);
        }
        return series.numFactory().zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        int endIndex = tradingRecord.getEndIndex(series);
        return tradingRecord.getPositions()
                .stream()
                .filter(position -> position.isClosed() && position.getExit().getIndex() <= endIndex)
                .map(t -> calculate(series, t))
                .reduce(series.numFactory().zero(), Num::plus);
    }

    /** The lower the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }
}
