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
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Volume indicator.
 * 体积 /卷 /容量指示器。
 *
 * 成交量指标是一种用于衡量金融市场中交易活动水平的指标。它衡量了在特定时间段内进行的交易量或资产的交易数量。成交量通常在柱状图中显示，它的变化可以提供有关市场参与程度和资金流动性的重要信息。
 *
 * 成交量指标通常用于分析市场趋势、价格变动和市场情绪。以下是一些成交量指标的常见用途：
 *
 * 1. **确认趋势**：成交量可以用来确认价格趋势的持续性。如果价格上涨伴随着增加的成交量，这可能表明上涨趋势的强劲，而如果价格上涨伴随着下降的成交量，可能会提示趋势转变的可能性。
 *
 * 2. **分析价格变动**：成交量可以用来分析价格变动的原因。例如，如果价格大幅上涨或下跌时成交量增加，这可能表示市场情绪的变化或大量交易者的参与。
 *
 * 3. **确认市场底部和顶部**：在市场底部和顶部，成交量通常会显示出明显的信号。例如，在市场底部，通常会出现成交量的放大，这可能是底部形成的信号。
 *
 * 4. **配合其他指标**：成交量经常与其他技术指标一起使用，例如移动平均线、MACD等，以提供更全面的市场分析和交易信号。
 *
 * 总的来说，成交量指标是技术分析中的重要工具，它提供了对市场活动水平和资金流动性的重要洞察，有助于交易者更好地理解市场趋势和做出交易决策。
 */
public class VolumeIndicator extends CachedIndicator<Num> {

    private int barCount;

    public VolumeIndicator(BarSeries series) {
        this(series, 1);
    }

    public VolumeIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        int startIndex = Math.max(0, index - barCount + 1);
        Num sumOfVolume = numOf(0);
        for (int i = startIndex; i <= index; i++) {
            sumOfVolume = sumOfVolume.plus(getBarSeries().getBar(i).getVolume());
        }
        return sumOfVolume;
    }
}