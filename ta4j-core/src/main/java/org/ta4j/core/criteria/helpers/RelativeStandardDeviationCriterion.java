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
 * Standard deviation criterion in percentage (also known as Coefficient of
 * Variation (CV)).
 *
 * <p>
 * Calculates the standard deviation in percentage for a Criterion.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/c/coefficientofvariation.asp">https://www.investopedia.com/terms/c/coefficientofvariation.asp</a>
 */
public class RelativeStandardDeviationCriterion extends AbstractAnalysisCriterion {

    /**
     * If true, then the lower the criterion value the better, otherwise the higher
     * the criterion value the better. This property is only used for
     * {@link #betterThan(Num, Num)}.
     */
    private final boolean lessIsBetter;

    private final StandardDeviationCriterion standardDeviationCriterion;
    private final AverageCriterion averageCriterion;

    /**
     * Constructor with {@link #lessIsBetter} = false.
     *
     * @param criterion the criterion from which the "relative standard deviation"
     *                  is calculated
     */
    public RelativeStandardDeviationCriterion(AnalysisCriterion criterion) {
        this.standardDeviationCriterion = new StandardDeviationCriterion(criterion);
        this.averageCriterion = new AverageCriterion(criterion);
        this.lessIsBetter = false;
    }

    /**
     * Constructor.
     *
     * @param criterion    the criterion from which the "relative standard
     *                     deviation" is calculated
     * @param lessIsBetter the {@link #lessIsBetter}
     */
    public RelativeStandardDeviationCriterion(AnalysisCriterion criterion, boolean lessIsBetter) {
        this.standardDeviationCriterion = new StandardDeviationCriterion(criterion);
        this.averageCriterion = new AverageCriterion(criterion);
        this.lessIsBetter = lessIsBetter;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num average = averageCriterion.calculate(series, position);
        return standardDeviationCriterion.calculate(series, position).dividedBy(average);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (tradingRecord.getPositions().isEmpty()) {
            return series.numFactory().zero();
        }
        Num average = averageCriterion.calculate(series, tradingRecord);
        return standardDeviationCriterion.calculate(series, tradingRecord).dividedBy(average);
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
