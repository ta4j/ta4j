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
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Sigma-Indicator (also called, "z-score" or "standard score").
 * 西格玛指标（也称为“z 分数”或“标准分数”）。
 *
 * Sigma指标，也称为“z-score”或“标准分数”，是一种用于衡量数据点与平均值之间偏离程度的统计指标。它表示一个数据点与平均值之间的标准偏差的倍数。Sigma指标常用于金融领域和统计学中，用于评估数据的正态性和相对位置。
 *
 * Sigma指标的计算方法如下：
 *
 *  Sigma  =  (X -  mu)  /  sigma
 *
 * 其中：
 * - \( X \) 是某个数据点的值。
 * - \( \mu \) 是数据集的平均值。
 * - \( \sigma \) 是数据集的标准偏差。
 *
 * Sigma指标表示了一个数据点相对于数据集平均值的偏离程度。如果数据点的Sigma值为正，则表示该数据点高于平均值；如果为负，则表示低于平均值。Sigma值的绝对值越大，表示数据点偏离平均值的程度越大。
 *
 * Sigma指标通常用于识别数据集中的异常值或离群点。通常，当Sigma值超过一定的阈值（例如2或3）时，就会被认为是异常值，可能需要进一步调查或处理。
 *
 * 在金融领域，Sigma指标也常用于风险管理和投资决策。例如，投资组合经理可能会使用Sigma指标来评估资产的相对表现或波动性，并作出相应的投资决策。
 *
 * see http://www.statisticshowto.com/probability-and-statistics/z-score/
 */
public class SigmaIndicator extends CachedIndicator<Num> {

    private Indicator<Num> ref;
    private int barCount;

    private SMAIndicator mean;
    private StandardDeviationIndicator sd;

    /**
     * Constructor.
     * 
     * @param ref      the indicator 指標
     * @param barCount the time frame 時間範圍
     */
    public SigmaIndicator(Indicator<Num> ref, int barCount) {
        super(ref);
        this.ref = ref;
        this.barCount = barCount;
        mean = new SMAIndicator(ref, barCount);
        sd = new StandardDeviationIndicator(ref, barCount);
    }

    @Override
    protected Num calculate(int index) {
        // z-score = (ref - mean) / sd
        return (ref.getValue(index).minus(mean.getValue(index))).dividedBy(sd.getValue(index));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
