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
 * Returns the total number of bars in all the positions.
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
        return tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(t -> calculate(series, t))
                .reduce(series.numFactory().zero(), Num::plus);
    }

    /** The lower the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }
}
