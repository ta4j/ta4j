/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Number of position criterion.
 */
public class NumberOfPositionsCriterion extends AbstractAnalysisCriterion {

    /**
     * If true, then the lower the criterion value the better, otherwise the higher
     * the criterion value the better. This property is only used for
     * {@link #betterThan(Num, Num)}.
     */
    private final boolean lessIsBetter;

    /**
     * Constructor with {@link #lessIsBetter} = true.
     */
    public NumberOfPositionsCriterion() {
        this.lessIsBetter = true;
    }

    /**
     * Constructor.
     *
     * @param lessIsBetter the {@link #lessIsBetter}
     */
    public NumberOfPositionsCriterion(boolean lessIsBetter) {
        this.lessIsBetter = lessIsBetter;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return series.numFactory().one();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        return series.numFactory().numOf(tradingRecord.getPositionCount());
    }

    /**
     * If {@link #lessIsBetter} == false, then the lower the criterion value, the
     * better, otherwise the higher the criterion value the better.
     */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return lessIsBetter ? criterionValue1.isLessThan(criterionValue2)
                : criterionValue1.isGreaterThan(criterionValue2);
    }
}
