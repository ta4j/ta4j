/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.ReturnCriterion;
import org.ta4j.core.num.Num;

/**
 * Calculates the average return per bar criterion, returned in decimal format.
 *
 * <p>
 * It uses the following formula to accurately capture the compounding effect of
 * returns over the specified number of bars:
 *
 * <pre>
 * AverageReturnPerBar = pow({@link ReturnCriterion gross return}, 1/ {@link NumberOfBarsCriterion number of bars})
 * </pre>
 */
public class AverageReturnPerBarCriterion extends AbstractAnalysisCriterion {

    private final ReturnCriterion grossReturn = new ReturnCriterion();
    private final NumberOfBarsCriterion numberOfBars = new NumberOfBarsCriterion();

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num bars = numberOfBars.calculate(series, position);
        // If a simple division was used (grossreturn/bars), compounding would not be
        // considered, leading to inaccuracies in the calculation.
        // Therefore we need to use "pow" to accurately capture the compounding effect.
        return bars.isZero() ? series.numFactory().one()
                : grossReturn.calculate(series, position).pow(series.numFactory().one().dividedBy(bars));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num bars = numberOfBars.calculate(series, tradingRecord);
        return bars.isZero() ? series.numFactory().one()
                : grossReturn.calculate(series, tradingRecord).pow(series.numFactory().one().dividedBy(bars));
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
