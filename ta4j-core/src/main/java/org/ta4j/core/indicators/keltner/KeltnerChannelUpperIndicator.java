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

import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Keltner Channel (upper line) indicator
 * 凯尔特纳通道（上行）指标
 *
 * 凯尔特纳通道（Keltner Channel）的上轨是其三条线中的一个重要组成部分，用于显示价格的上限和潜在的阻力水平。上轨是由一段时间内的指数移动平均线（EMA）和一定数量的平均真实范围（ATR）的标准差组成的。
 *
 * 上轨的计算方式如下：
 *
 * 上轨 = EMA + ATR * 倍数
 *
 * 其中：
 * - EMA 是一段时间内的指数移动平均线，通常选择 20 个周期。
 * - ATR 是一段时间内的平均真实范围，用于衡量价格的波动性。
 * - 倍数是一个可以调整的参数，通常为 2 或 2.5。
 *
 * 上轨代表了一定时间段内价格的上限水平，可以用作阻力线。当价格接近或触及上轨时，可能会出现卖出信号。如果价格突破上轨并保持在上轨之上，可能表明市场处于超买状态，并且价格可能会下跌。
 *
 * 总的来说，凯尔特纳通道的上轨是一个重要的技术指标，它可以帮助交易者识别价格的阻力水平，并提供潜在的卖出信号，有助于制定交易策略。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels</a>
 */
public class KeltnerChannelUpperIndicator extends CachedIndicator<Num> {

    private final ATRIndicator averageTrueRangeIndicator;

    private final KeltnerChannelMiddleIndicator keltnerMiddleIndicator;

    private final Num ratio;

    public KeltnerChannelUpperIndicator(KeltnerChannelMiddleIndicator keltnerMiddleIndicator, double ratio,
            int barCountATR) {
        super(keltnerMiddleIndicator);
        this.ratio = numOf(ratio);
        this.keltnerMiddleIndicator = keltnerMiddleIndicator;
        averageTrueRangeIndicator = new ATRIndicator(keltnerMiddleIndicator.getBarSeries(), barCountATR);
    }

    @Override
    protected Num calculate(int index) {
        return keltnerMiddleIndicator.getValue(index)
                .plus(ratio.multipliedBy(averageTrueRangeIndicator.getValue(index)));
    }

}
