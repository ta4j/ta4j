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
import org.ta4j.core.num.Num;

/**
 * Standard error indicator.
 * 标准错误指示器。
 *
 * 标准误差指标（Standard Error）是用于估计统计样本均值与总体均值之间的差异的一种统计量。它表示样本均值与总体均值之间的平均差异的标准偏差。
 *
 * 标准误差通常用于衡量样本均值的稳定性和可靠性。标准误差的计算方法取决于所使用的统计方法和样本类型。
 *
 * 在样本均值的情况下，标准误差的计算公式如下：
 *
 *   SE = s / sqrt{n}
 *
 * 其中：
 * - \( SE \) 是标准误差。
 * - \( s \) 是样本标准偏差。
 * - \( n \) 是样本容量（样本大小）。
 *
 * 标准误差越小，表示样本均值与总体均值之间的差异越小，样本均值对总体均值的估计越准确。标准误差的主要用途是在假设检验和置信区间估计中，用于计算统计量的标准差，进而评估样本均值的可靠性。
 *
 * 在金融领域，标准误差通常用于对样本数据进行统计推断，例如对某个投资策略的平均回报率进行置信区间估计，或者对某个投资组合的平均风险进行统计检验等。
 *
 */
public class StandardErrorIndicator extends CachedIndicator<Num> {

    private int barCount;

    private StandardDeviationIndicator sdev;

    /**
     * Constructor.
     * 
     * @param indicator the indicator 指標
     * @param barCount  the time frame 時間範圍
     */
    public StandardErrorIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.barCount = barCount;
        this.sdev = new StandardDeviationIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        return sdev.getValue(index).dividedBy(numOf(numberOfObservations).sqrt());
    }
}
