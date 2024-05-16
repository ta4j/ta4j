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

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Double exponential moving average indicator.
 * 双指数移动平均线指标。
 *
 * 双重指数移动平均（Double Exponential Moving Average，DEMA）是一种技术指标，用于平滑资产的价格数据，并帮助确定价格的趋势方向。
 *
 * 与传统的指数移动平均（EMA）相比，双重指数移动平均更加平滑，因为它对价格的变化更加敏感。这是通过将两次指数平滑应用于价格数据来实现的。
 *
 * DEMA的计算过程如下：
 *
 * 1. 计算一次指数移动平均（EMA1），通常使用一个固定的时间周期（例如，10个交易周期）。
 * 2. 计算二次指数移动平均（EMA2），同样使用相同的时间周期。
 * 3. 然后，计算DEMA，使用以下公式：
 *      DEMA = 2 * EMA1 - EMA2
 *
 * DEMA的主要优势在于它可以更有效地过滤掉价格数据中的噪音，从而更好地捕捉价格的长期趋势。这使得DEMA成为确定市场趋势的有用工具，特别是在波动性较高的市场中。
 *
 * 交易者通常会将DEMA与其他技术指标结合使用，例如移动平均线交叉、趋势线等，以确认价格的趋势方向，并据此制定交易策略。
 *
 * </p/>
 *
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Double_exponential_moving_average">
 *      https://en.wikipedia.org/wiki/Double_exponential_moving_average</a>
 */
public class DoubleEMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final EMAIndicator ema;
    private final EMAIndicator emaEma;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     *                  指标
     * @param barCount  the time frame
     *                  時間範圍
     */
    public DoubleEMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.barCount = barCount;
        this.ema = new EMAIndicator(indicator, barCount);
        this.emaEma = new EMAIndicator(ema, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return ema.getValue(index).multipliedBy(numOf(2)).minus(emaEma.getValue(index));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
