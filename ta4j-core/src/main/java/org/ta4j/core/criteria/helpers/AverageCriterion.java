/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.helpers;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.num.Num;

/**
 * Average criterion.
 *
 * <p>
 * Calculates the average of a Criterion by dividing it by the number of
 * positions.
 */
public class AverageCriterion extends AbstractAnalysisCriterion {

    /**
     * If true, then the lower the criterion value the better, otherwise the higher
     * the criterion value the better. This property is only used for
     * {@link #betterThan(Num, Num)}.
     */
    private final boolean lessIsBetter;

    private final AnalysisCriterion criterion;
    private final NumberOfPositionsCriterion numberOfPositionsCriterion = new NumberOfPositionsCriterion();

    /**
     * Constructor with {@link #lessIsBetter} = false.
     *
     * @param criterion the criterion from which the "average" is calculated
     */
    public AverageCriterion(AnalysisCriterion criterion) {
        this.criterion = criterion;
        this.lessIsBetter = false;
    }

    /**
     * Constructor.
     *
     * @param criterion    the criterion from which the "average" is calculated
     * @param lessIsBetter the {@link #lessIsBetter}
     */
    public AverageCriterion(AnalysisCriterion criterion, boolean lessIsBetter) {
        this.criterion = criterion;
        this.lessIsBetter = lessIsBetter;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, position);
        return criterion.calculate(series, position).dividedBy(numberOfPositions);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (tradingRecord.getPositions().isEmpty()) {
            return series.numFactory().zero();
        }
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, tradingRecord);
        return criterion.calculate(series, tradingRecord).dividedBy(numberOfPositions);
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
