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
 * Standard error criterion.
 *
 * <p>
 * Calculates the standard error for a Criterion.
 */
public class StandardErrorCriterion extends AbstractAnalysisCriterion {

    /**
     * If true, then the lower the criterion value the better, otherwise the higher
     * the criterion value the better. This property is only used for
     * {@link #betterThan(Num, Num)}.
     */
    private final boolean lessIsBetter;

    private final StandardDeviationCriterion standardDeviationCriterion;
    private final NumberOfPositionsCriterion numberOfPositionsCriterion = new NumberOfPositionsCriterion();

    /**
     * Constructor with {@link #lessIsBetter} = true.
     *
     * @param criterion the criterion from which the "standard deviation error" is
     *                  calculated
     */
    public StandardErrorCriterion(AnalysisCriterion criterion) {
        this.standardDeviationCriterion = new StandardDeviationCriterion(criterion);
        this.lessIsBetter = true;
    }

    /**
     * Constructor.
     *
     * @param criterion    the criterion from which the "standard deviation error"
     *                     is calculated
     * @param lessIsBetter the {@link #lessIsBetter}
     */
    public StandardErrorCriterion(AnalysisCriterion criterion, boolean lessIsBetter) {
        this.standardDeviationCriterion = new StandardDeviationCriterion(criterion);
        this.lessIsBetter = lessIsBetter;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, position);
        return standardDeviationCriterion.calculate(series, position).dividedBy(numberOfPositions.sqrt());
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (tradingRecord.getPositions().isEmpty()) {
            return series.numFactory().zero();
        }
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, tradingRecord);
        return standardDeviationCriterion.calculate(series, tradingRecord).dividedBy(numberOfPositions.sqrt());
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
