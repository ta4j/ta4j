/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.num.Num;

/**
 * Calculates the barr length of the maximum drawdown.
 */
public class MaximumDrawdownBarLengthCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null || position.getExit() == null) {
            return series.numFactory().zero();
        }
        var cashFlow = new CashFlow(series, position);
        return calculateMaximumDrawdownLength(series, null, cashFlow);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var cashFlow = new CashFlow(series, tradingRecord);
        return calculateMaximumDrawdownLength(series, tradingRecord, cashFlow);
    }

    /**
     * The lower the criterion value, the better.
     */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }

    private Num calculateMaximumDrawdownLength(BarSeries series, TradingRecord tradingRecord, CashFlow cashFlow) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();
        var maxPeak = zero;
        var peakIndex = series.getBeginIndex();
        var maximumDrawdown = zero;
        var maximumLength = 0;

        var beginIndex = tradingRecord == null ? series.getBeginIndex() : tradingRecord.getStartIndex(series);
        var endIndex = tradingRecord == null ? series.getEndIndex() : tradingRecord.getEndIndex(series);

        if (!series.isEmpty()) {
            for (var i = beginIndex; i <= endIndex; i++) {
                var value = cashFlow.getValue(i);
                if (value.isGreaterThan(maxPeak)) {
                    maxPeak = value;
                    peakIndex = i;
                }
                var drawdown = maxPeak.minus(value).dividedBy(maxPeak);
                if (drawdown.isGreaterThan(maximumDrawdown)) {
                    maximumDrawdown = drawdown;
                    maximumLength = i - peakIndex;
                }
            }
        }
        return numFactory.numOf(maximumLength);
    }

}