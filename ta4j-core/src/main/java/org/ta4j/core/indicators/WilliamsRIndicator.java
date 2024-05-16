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
 * William's R indicator.
 * 威廉 R 指标。
 *
 * 威廉指标（Williams %R）是一种基于价格动量的技术指标，用于衡量当前价格相对于一定期间内价格范围的位置，以及识别价格的超买和超卖情况。
 *
 * 威廉指标的计算过程相对简单，通常基于以下公式进行：
 *
 *  %R =  (H - C) / (H - L) * (-100)
 *
 * 其中：
 * - \( H \) 是选定周期内的最高价。
 * - \( L \) 是选定周期内的最低价。
 * - \( C \) 是当前收盘价。
 *
 * 威廉指标的数值通常在 -100 到 0 之间波动，其中 -100 表示价格在选定周期内的最低价，0 表示价格在选定周期内的最高价。
 *
 * 通常情况下，威廉指标的数值在 -20 到 -80 之间被认为是超买或超卖的区域，其中 -20 表示超买，-80 表示超卖。交易者经常使用这些区域来识别潜在的买入或卖出信号。
 *
 * 在实践中，威廉指标通常与其他技术指标和价格模式一起使用，以提供更全面的市场分析。例如，当威廉指标从超卖区域上升至 -50 以上时，可能暗示着价格即将上涨，为买入信号；相反，当威廉指标从超买区域下降至 -50 以下时，可能暗示着价格即将下跌，为卖出信号。
 *
 * 总的来说，威廉指标是一种常用的技术指标，用于衡量价格的超买和超卖情况，并辅助交易者制定买卖策略。
 *
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/w/williamsr.asp">https://www.investopedia.com/terms/w/williamsr.asp</a>
 */
public class WilliamsRIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> closePriceIndicator;
    private final int barCount;
    private final HighPriceIndicator highPriceIndicator;
    private final LowPriceIndicator lowPriceIndicator;
    private final Num multiplier;

    public WilliamsRIndicator(BarSeries barSeries, int barCount) {
        this(new ClosePriceIndicator(barSeries), barCount, new HighPriceIndicator(barSeries),
                new LowPriceIndicator(barSeries));
    }

    public WilliamsRIndicator(ClosePriceIndicator closePriceIndicator, int barCount,
            HighPriceIndicator highPriceIndicator, LowPriceIndicator lowPriceIndicator) {
        super(closePriceIndicator);
        this.closePriceIndicator = closePriceIndicator;
        this.barCount = barCount;
        this.highPriceIndicator = highPriceIndicator;
        this.lowPriceIndicator = lowPriceIndicator;
        this.multiplier = numOf(-100);
    }

    @Override
    protected Num calculate(int index) {
        HighestValueIndicator highestHigh = new HighestValueIndicator(highPriceIndicator, barCount);
        LowestValueIndicator lowestMin = new LowestValueIndicator(lowPriceIndicator, barCount);

        Num highestHighPrice = highestHigh.getValue(index);
        Num lowestLowPrice = lowestMin.getValue(index);

        return ((highestHighPrice.minus(closePriceIndicator.getValue(index)))
                .dividedBy(highestHighPrice.minus(lowestLowPrice))).multipliedBy(multiplier);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
