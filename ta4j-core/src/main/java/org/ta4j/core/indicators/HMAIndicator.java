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
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.Num;

/**
 * Hull moving average (HMA) indicator.
 * * 赫尔移动平均线 (HMA) 指标。
 *
 * Hull移动平均线（Hull Moving Average，HMA）是一种平滑价格数据的技术指标，旨在识别价格的趋势方向和市场的买卖信号。与传统的简单移动平均线（SMA）或指数移动平均线（EMA）不同，Hull移动平均线在平滑价格数据的同时，能够更好地减少滞后性和噪音，使得其更适用于快速市场。
 *
 * HMA的计算过程相对复杂，通常分为以下几个步骤：
 *
 * 1. 计算两个移动平均线：通常是两个不同周期的移动平均线，一个是较短周期的移动平均线（例如9个交易周期），另一个是较长周期的移动平均线（例如26个交易周期）。
 * 2. 计算两个移动平均线之间的差值。
 * 3. 对差值进行平滑处理：通常是通过应用加权移动平均线（WMA）的方式对差值进行平滑处理。
 * 4. 将平滑处理后的差值添加到移动平均线中间值（Midpoint）上，得到HMA值。
 *
 * HMA指标的优势之一是其能够更好地适应不同市场条件下的价格变化，具有更快的反应速度和较少的滞后性。这使得HMA在快速市场中更具有优势，并能够更准确地捕捉价格的短期趋势。
 *
 * 交易者通常会使用HMA作为确认价格趋势和制定买卖信号的工具。例如，当HMA向上穿越价格时，可能暗示着上涨趋势的开始，为买入信号；相反，当HMA向下穿越价格时，可能暗示着下跌趋势的开始，为卖出信号。
 *
 * 总的来说，HMA是一种有用的技术指标，可用于识别价格的趋势和制定交易策略。然而，像所有技术指标一样，单独使用HMA可能会导致误导，因此建议结合其他技术指标和价格模式进行综合分析。
 *
 * @see <a href="http://alanhull.com/hull-moving-average">
 *      http://alanhull.com/hull-moving-average</a>
 */
public class HMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final WMAIndicator sqrtWma;

    public HMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.barCount = barCount;

        WMAIndicator halfWma = new WMAIndicator(indicator, barCount / 2);
        WMAIndicator origWma = new WMAIndicator(indicator, barCount);

        Indicator<Num> indicatorForSqrtWma = new DifferenceIndicator(TransformIndicator.multiply(halfWma, 2), origWma);
        sqrtWma = new WMAIndicator(indicatorForSqrtWma, numOf(barCount).sqrt().intValue());
    }

    @Override
    protected Num calculate(int index) {
        return sqrtWma.getValue(index);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}
