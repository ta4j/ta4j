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
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Awesome oscillator. (AO)
 * 很棒的振荡器。 (AO)
 *
 * 令人敬畏的振荡器（Awesome Oscillator，AO）是一种技术指标，用于帮助交易者确认资产价格的趋势方向和趋势的强度。它是由比尔·威廉姆斯（Bill Williams）开发的，旨在捕捉市场的动能。
 *
 * Awesome Oscillator基于资产的简单移动平均线（SMA）和指数移动平均线（EMA）之间的差异来计算。它通过计算这两个移动平均线的差异来衡量市场的动能。
 *
 * Awesome Oscillator的计算方法如下：
 *
 * 1. 首先，计算资产的简单移动平均线（SMA）：
 *    - 通常，选择5个交易周期和34个交易周期分别计算简单移动平均线。
 *
 * 2. 然后，计算资产的指数移动平均线（EMA）：
 *    - 同样，选择5个交易周期和34个交易周期分别计算指数移动平均线。
 *
 * 3. 最后，计算Awesome Oscillator：
 *    - 将5个交易周期的SMA减去34个交易周期的SMA，得到快速移动平均线（Fast AO）。
 *    - 将5个交易周期的EMA减去34个交易周期的EMA，得到慢速移动平均线（Slow AO）。
 *    - 最终，将快速移动平均线减去慢速移动平均线，得到Awesome Oscillator的值。
 *
 * Awesome Oscillator的数值越高，表示市场动能越强劲，可能暗示着价格趋势的加速；反之，Awesome Oscillator的数值越低，表示市场动能较弱，可能暗示着价格趋势的减缓。
 *
 * 交易者通常将Awesome Oscillator与其他技术指标一起使用，例如移动平均线、MACD等，以确认价格趋势的方向和市场动能的强度，并据此制定交易策略。
 *
 *
 * see https://www.tradingview.com/wiki/Awesome_Oscillator_(AO)
 */
public class AwesomeOscillatorIndicator extends CachedIndicator<Num> {

    private final SMAIndicator sma5;

    private final SMAIndicator sma34;

    /**
     * Constructor.
     *
     * @param indicator    (normally {@link MedianPriceIndicator})
     *                     （通常是 {@link MedianPriceIndicator}）
     * @param barCountSma1 (normally 5)
     *                     （通常为 5 个）
     * @param barCountSma2 (normally 34)
     *                     （通常为 34）
     */
    public AwesomeOscillatorIndicator(Indicator<Num> indicator, int barCountSma1, int barCountSma2) {
        super(indicator);
        this.sma5 = new SMAIndicator(indicator, barCountSma1);
        this.sma34 = new SMAIndicator(indicator, barCountSma2);
    }

    /**
     * Constructor.
     *
     * @param indicator (normally {@link MedianPriceIndicator})
     *                  （通常是 {@link MedianPriceIndicator}）
     */
    public AwesomeOscillatorIndicator(Indicator<Num> indicator) {
        this(indicator, 5, 34);
    }

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public AwesomeOscillatorIndicator(BarSeries series) {
        this(new MedianPriceIndicator(series), 5, 34);
    }

    @Override
    protected Num calculate(int index) {
        return sma5.getValue(index).minus(sma34.getValue(index));
    }
}
