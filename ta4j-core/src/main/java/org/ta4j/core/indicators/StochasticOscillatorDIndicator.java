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
 * Stochastic oscillator D.
 * 随机振荡器 D.
 *
 * 随机振荡器D（Stochastic Oscillator D）是随机振荡器指标的一种变体，用于衡量价格相对于给定时间范围内价格范围的位置。
 *
 * 随机振荡器指标（Stochastic Oscillator）通常包括两条线：K线和D线。K线是主要线，用于衡量当前收盘价相对于一段时间内价格范围的位置。D线则是K线的平滑线，通常通过对K线进行平均计算得到。
 *
 * Stochastic Oscillator D的计算过程如下：
 *
 * 1. 首先计算随机振荡器的K线，通常使用最近几个周期内的收盘价相对于价格范围的位置来计算。
 * 2. 然后对K线进行平滑处理，通常采用简单移动平均线或指数移动平均线等方法，得到D线。
 *
 * Stochastic Oscillator D指标的数值通常在0到100之间波动。和K线相比，D线更加平滑，因此更适合用于确认价格趋势和制定买卖信号。
 *
 * 交易者通常使用Stochastic Oscillator D指标来确认价格的超买和超卖区域，以及确认价格趋势的转折点。例如，当D线从超买区域跌入超卖区域时，可能暗示价格即将上涨，为买入信号；相反，当D线从超卖区域上升至超买区域时，可能暗示价格即将下跌，为卖出信号。
 *
 * 总的来说，Stochastic Oscillator D指标是一种用于衡量价格相对位置和确认价格趋势的技术指标，可以帮助交易者更好地理解市场的价格动态并制定相应的交易策略。
 *
 * Receive {@link StochasticOscillatorKIndicator} and returns its {@link SMAIndicator SMAIndicator(3)}.
 * * 接收 {@link StochasticOscillatorKIndicator} 并返回其 {@link SMAIndicator SMAIndicator(3)}。
 */
public class StochasticOscillatorDIndicator extends CachedIndicator<Num> {

    private Indicator<Num> indicator;

    public StochasticOscillatorDIndicator(StochasticOscillatorKIndicator k) {
        this(new SMAIndicator(k, 3));
    }

    public StochasticOscillatorDIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {
        return indicator.getValue(index);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + indicator;
    }
}
