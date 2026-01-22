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
 * Variance criterion.
 *
 * <p>
 * Calculates the variance for a Criterion.
 */
public class VarianceCriterion extends AbstractAnalysisCriterion {

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
     * @param criterion the criterion from which the "variance" is calculated
     */
    public VarianceCriterion(AnalysisCriterion criterion) {
        this.criterion = criterion;
        this.lessIsBetter = false;
    }

    /**
     * Constructor.
     *
     * @param criterion    the criterion from which the "variance" is calculated
     * @param lessIsBetter the {@link #lessIsBetter}
     */
    public VarianceCriterion(AnalysisCriterion criterion, boolean lessIsBetter) {
        this.criterion = criterion;
        this.lessIsBetter = lessIsBetter;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num criterionValue = criterion.calculate(series, position);
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, position);

        Num variance = series.numFactory().zero();
        Num average = criterionValue.dividedBy(numberOfPositions);
        Num pow = criterion.calculate(series, position).minus(average).pow(2);
        variance = variance.plus(pow);
        variance = variance.dividedBy(numberOfPositions);
        return variance;
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (tradingRecord.getPositions().isEmpty()) {
            return series.numFactory().zero();
        }
        Num criterionValue = criterion.calculate(series, tradingRecord);
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, tradingRecord);

        Num variance = series.numFactory().zero();
        Num average = criterionValue.dividedBy(numberOfPositions);

        for (Position position : tradingRecord.getPositions()) {
            Num pow = criterion.calculate(series, position).minus(average).pow(2);
            variance = variance.plus(pow);
        }
        variance = variance.dividedBy(numberOfPositions);
        return variance;
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
