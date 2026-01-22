/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Number of closed losing positions criterion.
 */
public class NumberOfLosingPositionsCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        return position.hasLoss() ? series.numFactory().one() : series.numFactory().zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        long numberOfLosingPositions = tradingRecord.getPositions().stream().filter(Position::hasLoss).count();
        return series.numFactory().numOf(numberOfLosingPositions);
    }

    /** The lower the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }

}
