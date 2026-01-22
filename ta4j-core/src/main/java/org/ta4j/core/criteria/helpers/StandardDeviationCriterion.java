/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.helpers;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Standard deviation criterion.
 *
 * <p>
 * Calculates the standard deviation for a Criterion.
 */
public class StandardDeviationCriterion extends AbstractAnalysisCriterion {

    /**
     * If true, then the lower the criterion value the better, otherwise the higher
     * the criterion value the better. This property is only used for
     * {@link #betterThan(Num, Num)}.
     */
    private final boolean lessIsBetter;

    private final VarianceCriterion varianceCriterion;

    /**
     * Constructor with {@link #lessIsBetter} = false.
     *
     * @param criterion the criterion from which the "standard deviation" is
     *                  calculated
     */
    public StandardDeviationCriterion(AnalysisCriterion criterion) {
        this.varianceCriterion = new VarianceCriterion(criterion);
        this.lessIsBetter = false;
    }

    /**
     * Constructor.
     *
     * @param criterion    the criterion from which the "standard deviation" is
     *                     calculated
     * @param lessIsBetter the {@link #lessIsBetter}
     */
    public StandardDeviationCriterion(AnalysisCriterion criterion, boolean lessIsBetter) {
        this.varianceCriterion = new VarianceCriterion(criterion);
        this.lessIsBetter = lessIsBetter;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return varianceCriterion.calculate(series, position).sqrt();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (tradingRecord.getPositions().isEmpty()) {
            return series.numFactory().zero();
        }
        return varianceCriterion.calculate(series, tradingRecord).sqrt();
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
