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
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.NumberOfLosingPositionsCriterion;
import org.ta4j.core.num.Num;

/**
 * Average gross loss (with commissions).
 */
public class AverageLossCriterion extends AbstractAnalysisCriterion {

    private final NumberOfLosingPositionsCriterion numberOfLosingPositionsCriterion = new NumberOfLosingPositionsCriterion();
    private final GrossLossCriterion grossLossCriterion = new GrossLossCriterion();

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num numberOfLosingPositions = numberOfLosingPositionsCriterion.calculate(series, position);
        if (numberOfLosingPositions.isZero()) {
            return series.numOf(0);
        }
        Num grossLoss = grossLossCriterion.calculate(series, position);
        if (grossLoss.isZero()) {
            return series.numOf(0);
        }
        return grossLoss.dividedBy(numberOfLosingPositions);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num numberOfLosingPositions = numberOfLosingPositionsCriterion.calculate(series, tradingRecord);
        if (numberOfLosingPositions.isZero()) {
            return series.numOf(0);
        }
        Num grossLoss = grossLossCriterion.calculate(series, tradingRecord);
        if (grossLoss.isZero()) {
            return series.numOf(0);
        }
        return grossLoss.dividedBy(numberOfLosingPositions);
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
