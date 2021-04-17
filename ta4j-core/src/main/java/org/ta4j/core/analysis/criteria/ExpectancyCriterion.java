/**
 * The MIT License (MIT)
 * <p>
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
 * authors (see AUTHORS)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
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
 * Expectancy is the expected return of the strategy.
 * https://www.learningmarkets.com/determining-expectancy-in-your-trading/
 *
 * This is a simple process of multiplying the reward to risk ratio (5) by the
 * percentage of winning trades (28%), and subtracting the percentage of losing
 * trades (72%), which is calculated like this:
 *
 * (Reward to Risk ratio x win ratio) – Loss ratio = Expectancy Ratio
 *
 * (5*28%) – (72%) = .68
 *
 * Superficially, this means that on average you expect this strategy’s trades
 * to return .68 times the size of your losers. This is important for two
 * reasons: First, it may seem obvious, but you know right away that you have a
 * positive return. Second, you now have a number you can compare to other
 * candidate systems to make decisions about which ones you employ.
 *
 * It is important to remember that any system with an expectancy greater than 0
 * is profitable using past data. The key is finding one that will be profitable
 * in the future.
 *
 * You can also use this number to evaluate the effectiveness of modifications
 * to this system.
 */
public class ExpectancyCriterion extends AbstractAnalysisCriterion {

    private final RewardToRiskCriterion rewardRisk = new RewardToRiskCriterion();
    private final AnalysisCriterion winRatio = new WinningPositionsRatioCriterion();
    private final AnalysisCriterion lossRatio = new LosingPositionsRatioCriterion();

    @Override
    public Num calculate(BarSeries series, Position position) {
        throw new UnsupportedOperationException("Cannot calculate Reward to Risk ratio from one position");
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        final Num rrValue = rewardRisk.calculate(series, tradingRecord);
        final Num winRatioValue = winRatio.calculate(series, tradingRecord);
        final Num lossRatioValue = lossRatio.calculate(series, tradingRecord);

        return rrValue.multipliedBy(winRatioValue).minus(lossRatioValue);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
