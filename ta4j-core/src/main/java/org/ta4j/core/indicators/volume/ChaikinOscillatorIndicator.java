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
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Chaikin Oscillator.
 * 柴金振荡器。
 *
 * 查金振荡器（Chaikin Oscillator）是一种基于资金流动性的技术指标，用于衡量资产价格的动量和趋势的变化。它是由马克·查金（Marc Chaikin）开发的，结合了价格和成交量的信息，旨在帮助识别买卖信号和价格趋势的转折点。
 *
 * 查金振荡器的计算过程如下：
 *
 * 1. 计算累积分布线（Accumulation Distribution Line，ADL）：
 *    - 使用成交量加权平均价格（Typical Price）来计算每个交易周期的成交量流入。
 *    - 累积分布线是根据成交量流入的累积值计算得到的。
 *
 * 2. 计算快速移动平均线（Fast Moving Average）：
 *    - 使用ADL计算快速移动平均线，通常是使用较短的时间周期。
 *
 * 3. 计算慢速移动平均线（Slow Moving Average）：
 *    - 使用ADL计算慢速移动平均线，通常是使用较长的时间周期。
 *
 * 4. 计算查金振荡器值：
 *    - 查金振荡器值等于快速移动平均线减去慢速移动平均线的差值。
 *
 * 查金振荡器的数值可正可负。当查金振荡器值为正时，表示快速移动平均线高于慢速移动平均线，可能暗示着价格上涨的动量正在增强；当查金振荡器值为负时，表示快速移动平均线低于慢速移动平均线，可能暗示着价格下跌的动量正在增强。
 *
 * 交易者可以利用查金振荡器来识别价格趋势的转折点和交易信号。例如，当查金振荡器从负值转变为正值时，可能意味着价格上涨的趋势即将开始；相反，当查金振荡器从正值转变为负值时，可能意味着价格下跌的趋势即将开始。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_oscillator">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_oscillator</a>
 */
public class ChaikinOscillatorIndicator extends CachedIndicator<Num> {

    private final EMAIndicator emaShort;
    private final EMAIndicator emaLong;

    /**
     * Constructor.
     *
     * @param series        the {@link BarSeries}
     *                      {@link BarSeries}
     * @param shortBarCount (usually 3)
     *                      （通常是 3 个）
     * @param longBarCount  (usually 10)
     *                      （通常为 10 个）
     */
    public ChaikinOscillatorIndicator(BarSeries series, int shortBarCount, int longBarCount) {
        super(series);
        this.emaShort = new EMAIndicator(new AccumulationDistributionIndicator(series), shortBarCount);
        this.emaLong = new EMAIndicator(new AccumulationDistributionIndicator(series), longBarCount);
    }

    /**
     * Constructor.
     *
     * @param series the {@link BarSeries}
     *               {@link BarSeries}
     */
    public ChaikinOscillatorIndicator(BarSeries series) {
        this(series, 3, 10);
    }

    @Override
    protected Num calculate(int index) {
        return emaShort.getValue(index).minus(emaLong.getValue(index));
    }
}
