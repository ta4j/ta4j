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
package org.ta4j.core.indicators.keltner;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Keltner Channel (middle line) indicator
 * 凯尔特纳通道（中线）指标
 *
 * 凯尔特纳通道（Keltner Channel）的中轨是其三条线中的一个重要组成部分，通常用于识别价格的趋势和确定交易信号。中轨是由一段时间内的指数移动平均线（EMA）计算得出的，通常代表了价格的中间水平。
 *
 * 中轨的计算方式如下：
 *
 * 中轨 = EMA
 *
 * 其中：
 * - EMA 是一段时间内的指数移动平均线，通常选择 20 个周期。
 *
 * 中轨代表了一定时间段内价格的平均水平，可以用作价格趋势的参考线。当价格位于中轨之上时，可能表明市场处于上升趋势；而当价格位于中轨之下时，则可能表明市场处于下降趋势。
 *
 * 凯尔特纳通道的中轨也可以用于识别交易信号。例如，当价格从下方穿越中轨向上时，可能出现买入信号；而当价格从上方穿越中轨向下时，则可能出现卖出信号。
 *
 * 总的来说，凯尔特纳通道的中轨是一个重要的技术指标，它可以提供有关价格趋势和交易信号的信息，有助于交易者更好地理解市场动态并制定相应的交易策略。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels</a>
 */
public class KeltnerChannelMiddleIndicator extends CachedIndicator<Num> {

    private final EMAIndicator emaIndicator;

    public KeltnerChannelMiddleIndicator(BarSeries series, int barCountEMA) {
        this(new TypicalPriceIndicator(series), barCountEMA);
    }

    public KeltnerChannelMiddleIndicator(Indicator<Num> indicator, int barCountEMA) {
        super(indicator);
        emaIndicator = new EMAIndicator(indicator, barCountEMA);
    }

    @Override
    protected Num calculate(int index) {
        return emaIndicator.getValue(index);
    }

}
