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
package org.ta4j.core.analysis.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.analysis.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.num.Num;

/**
 * Average gross profit or loss (with commissions).
 */
public class AverageCriterion extends AbstractAnalysisCriterion {

    private final NumberOfPositionsCriterion numberOfPositionsCriterion;
    private final GrossCriterion grossCriterion;

    /**
     * Constructor.
     * 
     * @param positionFilter consider either the profit or the loss position
     */
    public AverageCriterion(PositionFilter positionFilter) {
        numberOfPositionsCriterion = new NumberOfPositionsCriterion(positionFilter);
        grossCriterion = new GrossCriterion(positionFilter);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, position);
        if (numberOfPositions.isZero()) {
            return series.numOf(0);
        }
        Num gross = grossCriterion.calculate(series, position);
        if (gross.isZero()) {
            return series.numOf(0);
        }
        return gross.dividedBy(numberOfPositions);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, tradingRecord);
        if (numberOfPositions.isZero()) {
            return series.numOf(0);
        }
        Num gross = grossCriterion.calculate(series, tradingRecord);
        if (gross.isZero()) {
            return series.numOf(0);
        }
        return gross.dividedBy(numberOfPositions);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
