/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import java.util.ArrayList;
import java.util.List;

/**
 * Reward risk ratio criterion.
 * <p>
 * (i.e. the {@link TotalProfitCriterion total profit} over the {@link MaximumDrawdownCriterion maximum drawdown}.
 */
public class RewardRiskRatioCriterion extends AbstractAnalysisCriterion {

    private AnalysisCriterion totalProfit = new TotalProfitCriterion();

    private AnalysisCriterion maxDrawdown = new MaximumDrawdownCriterion();

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        return totalProfit.calculate(series, trades) / maxDrawdown.calculate(series, trades);
    }

    @Override
    public boolean betterThan(double criterionValue1, double criterionValue2) {
        return criterionValue1 > criterionValue2;
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        List<Trade> trades = new ArrayList<Trade>();
        trades.add(trade);
        return calculate(series, trades);
    }
}
