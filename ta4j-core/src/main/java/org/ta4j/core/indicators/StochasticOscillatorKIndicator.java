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
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Stochastic oscillator K.
 * 随机震荡指标 K。
 *
 * Receives barSeries and barCount and calculates the
 StochasticOscillatorKIndicator over ClosePriceIndicator, or receives an
 indicator, HighPriceIndicator and LowPriceIndicator and returns
 StochasticOsiclatorK over this indicator.

 接收 barSeries 和 barCount 并计算 StochasticOscillatorKIndicator over ClosePriceIndicator，或接收指标 HighPriceIndicator 和 LowPriceIndicator 并返回 StochasticOsiclatorK over 此指标。


 随机振荡器K（Stochastic Oscillator K）是一种常用的技术指标，用于衡量当前价格相对于一段时间内价格范围的位置，以及识别价格的超买和超卖情况。

 随机振荡器K指标的计算基于最近一段时间内的价格范围和当前收盘价之间的关系。通常，随机振荡器K指标由两条线组成：主要的%K线和辅助的%D线。%K线是基于当前价格和一段时间内的最高价和最低价之间的比例计算得出的，而%D线则是对%K线进行平滑处理得到的。

 随机振荡器K指标的计算过程如下：

 1. 计算最近一段时间内的价格范围，通常是N个交易周期内的最高价和最低价之差。
 2. 计算当前收盘价与价格范围的比例，得到%K线的数值。
 3. 可选：对%K线进行平滑处理，通常采用简单移动平均线或指数移动平均线等方法，得到%D线的数值。

 随机振荡器K指标的数值范围通常在0到100之间。当%K线高于80时，表示市场可能处于超买状态，可能会出现价格下跌的信号；当%K线低于20时，表示市场可能处于超卖状态，可能会出现价格上涨的信号。

 交易者通常使用随机振荡器K指标来确认价格的超买和超卖情况，以及识别价格趋势的转折点。例如，当%K线从超买区域跌入超卖区域时，可能暗示价格即将上涨，为买入信号；相反，当%K线从超卖区域上升至超买区域时，可能暗示价格即将下跌，为卖出信号。

 总的来说，随机振荡器K指标是一种用于衡量价格相对位置和识别价格超买和超卖情况的常用技术指标，可以帮助交易者更好地理解市场的价格动态并制定相应的交易策略。

 */
public class StochasticOscillatorKIndicator extends CachedIndicator<Num> {
    private final Indicator<Num> indicator;

    private final int barCount;

    private HighPriceIndicator highPriceIndicator;

    private LowPriceIndicator lowPriceIndicator;

    public StochasticOscillatorKIndicator(BarSeries barSeries, int barCount) {
        this(new ClosePriceIndicator(barSeries), barCount, new HighPriceIndicator(barSeries),
                new LowPriceIndicator(barSeries));
    }

    public StochasticOscillatorKIndicator(Indicator<Num> indicator, int barCount, HighPriceIndicator highPriceIndicator,
            LowPriceIndicator lowPriceIndicator) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.highPriceIndicator = highPriceIndicator;
        this.lowPriceIndicator = lowPriceIndicator;
    }

    @Override
    protected Num calculate(int index) {
        HighestValueIndicator highestHigh = new HighestValueIndicator(highPriceIndicator, barCount);
        LowestValueIndicator lowestMin = new LowestValueIndicator(lowPriceIndicator, barCount);

        Num highestHighPrice = highestHigh.getValue(index);
        Num lowestLowPrice = lowestMin.getValue(index);

        return indicator.getValue(index).minus(lowestLowPrice).dividedBy(highestHighPrice.minus(lowestLowPrice))
                .multipliedBy(numOf(100));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
