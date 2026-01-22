/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Number of break even position criterion.
 */
public class NumberOfBreakEvenPositionsCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        return isBreakEvenPosition(position) ? series.numFactory().one() : series.numFactory().zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        long numberOfBreakEvenTrades = tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .filter(this::isBreakEvenPosition)
                .count();
        return series.numFactory().numOf(numberOfBreakEvenTrades);
    }

    private boolean isBreakEvenPosition(Position position) {
        return position.isClosed() && position.getProfit().isZero();
    }

    /** The lower the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }
}
