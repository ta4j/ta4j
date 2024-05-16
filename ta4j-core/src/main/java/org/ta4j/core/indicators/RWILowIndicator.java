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
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * The Class RandomWalkIndexLowIndicator.
 * 类随机游走指数低指标。
 *
 * 随机漫步指数低点指标（Random Walk Index Low Indicator）是一种技术指标，用于确定价格趋势的强度和市场的超卖状态。
 *
 * 该指标是基于随机漫步指数（Random Walk Index，RWI）而来的。RWI是一种由J. L. Bollinger提出的技术指标，用于测量价格变动的波动性和趋势的强度。而随机漫步指数低点指标则是根据随机漫步指数计算出的低点值。
 *
 * 随机漫步指数的计算过程相对复杂，它涉及到价格变动的绝对值、方向性和随机性等因素。基本上，RWI的计算是基于价格变动的统计性质，用于衡量市场的不确定性程度。
 *
 * 随机漫步指数低点指标则是在随机漫步指数的基础上，通过比较过去一段时间内随机漫步指数的最低值来确定市场的超卖状态。当随机漫步指数低于历史最低值时，可能暗示着市场的超卖状态，可能会出现价格反弹或反转的信号。
 *
 * 交易者通常使用随机漫步指数低点指标来确认价格趋势的强度和识别市场的超卖状态，并根据市场情况制定相应的交易策略。
 *
 * 总的来说，随机漫步指数低点指标是一种用于确定市场超卖状态的技术指标，可以帮助交易者更好地理解市场的价格动态和趋势变化，从而制定更加有效的交易策略。
 *
 * @see <a href=
 *      "http://https://rtmath.net/helpFinAnalysis/html/934563a8-9171-42d2-8444-486691234b1d.html">Source
 *      of formular</a>
 */
public class RWILowIndicator extends CachedIndicator<Num> {

    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the series
     *                 该系列
     * @param barCount the time frame
     *                 时间范围
     */
    public RWILowIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        if (index - barCount + 1 < getBarSeries().getBeginIndex()) {
            return NaN.NaN;
        }

        Num minRWIL = numOf(0);
        for (int n = 2; n <= barCount; n++) {
            minRWIL = minRWIL.max(calcRWIHFor(index, n));
        }

        return minRWIL;
    }

    private Num calcRWIHFor(final int index, final int n) {
        BarSeries series = getBarSeries();
        Num low = series.getBar(index).getLowPrice();
        Num highN = series.getBar(index + 1 - n).getHighPrice();
        Num atrN = new ATRIndicator(series, n).getValue(index);
        Num sqrtN = numOf(n).sqrt();

        return highN.minus(low).dividedBy(atrN.multipliedBy(sqrtN));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
