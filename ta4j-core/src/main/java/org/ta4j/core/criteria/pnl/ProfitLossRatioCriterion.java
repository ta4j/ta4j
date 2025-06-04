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
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Profit/Loss ratio criterion.
 *
 * <p>
 * Defined as the average net profit divided by the average net loss. Both
 * averages include trading costs; that is, costs are subtracted from each
 * position before computing the mean values. The ratio is returned in decimal
 * format.
 */
public class ProfitLossRatioCriterion extends AbstractAnalysisCriterion {

    private final NetAverageProfitCriterion averageProfitCriterion = new NetAverageProfitCriterion();
    private final NetAverageLossCriterion averageLossCriterion = new NetAverageLossCriterion();

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num averageProfit = averageProfitCriterion.calculate(series, position);
        if (averageProfit.isZero()) {
            // only losing positions means a ratio of 0
            return series.numFactory().zero();
        }
        Num averageLoss = averageLossCriterion.calculate(series, position);
        if (averageLoss.isZero()) {
            // only winning positions means a ratio of 1
            return series.numFactory().one();
        }
        return averageProfit.dividedBy(averageLoss).abs();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num averageProfit = averageProfitCriterion.calculate(series, tradingRecord);
        if (averageProfit.isZero()) {
            // only losing positions means a ratio of 0
            return series.numFactory().zero();
        }
        Num averageLoss = averageLossCriterion.calculate(series, tradingRecord);
        if (averageLoss.isZero()) {
            // only winning positions means a ratio of 1
            return series.numFactory().one();
        }
        return averageProfit.dividedBy(averageLoss).abs();
    }

}
