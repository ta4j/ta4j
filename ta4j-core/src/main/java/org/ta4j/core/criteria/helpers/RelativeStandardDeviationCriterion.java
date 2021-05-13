/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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

    private final StandardDeviationCriterion standardDeviationCriterion;
    private final AverageCriterion averageCriterion;

    /**
     * Constructor.
     * 
     * @param criterion the criterion from which the "relative standard deviation"
     *                  is calculated
     */
    public RelativeStandardDeviationCriterion(AnalysisCriterion criterion) {
        this.standardDeviationCriterion = new StandardDeviationCriterion(criterion);
        this.averageCriterion = new AverageCriterion(criterion);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num average = averageCriterion.calculate(series, position);
        return standardDeviationCriterion.calculate(series, position).dividedBy(average);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (tradingRecord.getPositions().isEmpty()) {
            return series.numOf(0);
        }
        Num average = averageCriterion.calculate(series, tradingRecord);
        return standardDeviationCriterion.calculate(series, tradingRecord).dividedBy(average);
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
