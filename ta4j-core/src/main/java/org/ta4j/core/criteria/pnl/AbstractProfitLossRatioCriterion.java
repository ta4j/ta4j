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
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Base class for profit/loss ratio criteria.
 * <p>
 * Calculates the ratio of the average profit over the average loss.
 */
public abstract class AbstractProfitLossRatioCriterion extends AbstractAnalysisCriterion {

    private final AnalysisCriterion averageProfitCriterion;
    private final AnalysisCriterion averageLossCriterion;

    protected AbstractProfitLossRatioCriterion(AnalysisCriterion averageProfitCriterion,
            AnalysisCriterion averageLossCriterion) {
        this.averageProfitCriterion = averageProfitCriterion;
        this.averageLossCriterion = averageLossCriterion;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var averageProfit = averageProfitCriterion.calculate(series, position);
        var averageLoss = averageLossCriterion.calculate(series, position);
        return calculateRatio(series, averageProfit, averageLoss);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var averageProfit = averageProfitCriterion.calculate(series, tradingRecord);
        var averageLoss = averageLossCriterion.calculate(series, tradingRecord);
        return calculateRatio(series, averageProfit, averageLoss);
    }

    private Num calculateRatio(BarSeries series, Num averageProfit, Num averageLoss) {
        var numFactory = series.numFactory();
        if (averageProfit.isZero()) {
            return numFactory.zero();
        }
        if (averageLoss.isZero()) {
            return numFactory.one();
        }
        return averageProfit.dividedBy(averageLoss).abs();
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
