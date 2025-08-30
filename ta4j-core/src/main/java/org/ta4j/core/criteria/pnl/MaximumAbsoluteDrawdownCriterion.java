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

/**
 * Criterion that calculates the <b>maximum absolute drawdown</b> of an equity
 * curve.
 * <p>
 * The maximum absolute drawdown is the largest observed decline from a
 * cumulative profit peak to a subsequent trough, expressed in absolute terms
 * rather than relative percentage. It is a measure of downside risk and capital
 * exposure during a trading period.
 */
public final class MaximumAbsoluteDrawdownCriterion extends AbstractAnalysisCriterion {

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

    /**
     * Scans a cumulative PnL curve to find the maximum drawdown. The algorithm
     * walks through the equity curve, recording peaks and computing the largest
     * decline to a trough thereafter.
     *
     * @param series the bar series
     * @param record the trading record (optional, may be null)
     * @param pnl    the cumulative profit-and-loss curve
     * @return the maximum drawdown
     */
    private Num scan(BarSeries series, TradingRecord record, CumulativePnL pnl) {
        var numFactory = series.numFactory();
        var begin = (record == null) ? series.getBeginIndex() : record.getStartIndex(series);
        var end = (record == null) ? series.getEndIndex() : record.getEndIndex(series);

        var peak = numFactory.zero();
        var maxDrawDown = numFactory.zero();

        for (var i = begin; i <= end; i++) {
            var value = pnl.getValue(i);
            if (value.isGreaterThan(peak)) {
                peak = value;
            }
            var drawDown = peak.minus(value);
            if (drawDown.isGreaterThan(maxDrawDown)) {
                maxDrawDown = drawDown;
            }
        }
        return maxDrawDown;
    }

}