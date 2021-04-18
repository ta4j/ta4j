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

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * The risk to reward ratio is the average size of a profitable trade divided by
 * the average size of a losing trade. Imagine that in your trading strategy
 * your winners are 5 times larger than your losers. That will make your reward
 * to risk ratio 5.
 * <p>
 * ($500 Winner size) / ($100 Loser size) = 5
 */
public class RewardToRiskCriterion extends AbstractAnalysisCriterion {

    private AnalysisCriterion averageWin = new AverageProfitCriterion();
    private AnalysisCriterion averageLoss = new AverageLossCriterion();

    @Override
    public Num calculate(BarSeries series, Position position) {
        throw new UnsupportedOperationException("Cannot calculate Reward to Risk ratio from one position");
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        final Num averageLossValue = averageLoss.calculate(series, tradingRecord);
        final Num averageWinValue = averageWin.calculate(series, tradingRecord);

        return averageWinValue.dividedBy(averageLossValue);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
