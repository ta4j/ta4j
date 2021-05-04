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
package org.ta4j.core.analysis.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Number of maximum consecutive losing position criterion.
 */
public class NumberOfConsecutiveLosingPositionsCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        return isLosingPosition(position) ? series.numOf(1) : series.numOf(0);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        int maxConsecutive = 0;
        int consecutives = 0;
        for (Position position : tradingRecord.getPositions()) {
            if (isLosingPosition(position)) {
                consecutives = consecutives + 1;
            } else {
                if (maxConsecutive < consecutives) {
                    maxConsecutive = consecutives;
                }
                consecutives = 0; // reset
            }
        }

        // in case all positions are losing positions
        if (maxConsecutive < consecutives) {
            maxConsecutive = consecutives;
        }

        return series.numOf(maxConsecutive);
    }

    /** The lower the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }

    private boolean isLosingPosition(Position position) {
        if (position.isClosed()) {
            return position.getProfit().isNegative();
        }
        return false;
    }
}
