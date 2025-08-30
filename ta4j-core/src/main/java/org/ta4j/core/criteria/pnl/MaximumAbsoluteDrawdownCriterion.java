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
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CumulativePnL;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

public final class MaximumAbsoluteDrawdownCriterion extends AbstractAnalysisCriterion {

    private final Num pointValueOrNull;

    public MaximumAbsoluteDrawdownCriterion() {
        this.pointValueOrNull = null;
    }

    public MaximumAbsoluteDrawdownCriterion(Num pointValue) {
        this.pointValueOrNull = pointValue;
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var pnl = new CumulativePnL(series, tradingRecord);
        return scan(series, tradingRecord, pnl);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null || position.getExit() == null) {
            return series.numFactory().zero();
        }
        var pnl = new CumulativePnL(series, position);
        return scan(series, null, pnl);
    }

    @Override
    public boolean betterThan(Num first, Num second) {
        return first.isLessThan(second);
    }

    private Num scan(BarSeries series, TradingRecord record, CumulativePnL pnl) {
        var numFactory = series.numFactory();
        var begin = (record == null) ? series.getBeginIndex() : record.getStartIndex(series);
        var end = (record == null) ? series.getEndIndex() : record.getEndIndex(series);

        var peak = numFactory.zero();
        var maxDd = numFactory.zero();

        for (var i = begin; i <= end; i++) {
            var value = pnl.getValue(i);
            if (value.isGreaterThan(peak)) {
                peak = value;
            }
            var dd = peak.minus(value);
            if (dd.isGreaterThan(maxDd)) {
                maxDd = dd;
            }
        }
        return (pointValueOrNull == null) ? maxDd : maxDd.multipliedBy(pointValueOrNull);
    }

}