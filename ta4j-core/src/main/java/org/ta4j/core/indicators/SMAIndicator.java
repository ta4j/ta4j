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
 * Simple moving average (SMA) indicator.
 * 简单移动平均线 (SMA) 指标。
 *
 * 简单移动平均线（Simple Moving Average，SMA）指标是一种基本的技术分析工具，用于平滑价格数据并识别价格的趋势。
 *
 * SMA指标的计算方法非常简单，只需将一定周期内的价格数据相加，然后除以周期数即可得到移动平均值。例如，如果选择10个交易周期作为计算周期，则SMA指标的计算公式如下：
 *
 *   SMA =  P_1 + P_2 + P_3 + ... + P_10  / 10
 *
 * 其中，( P_1, P_2, P_3, \ldots, P_{10} ) 分别代表最近的10个交易周期内的价格数据。
 *
 * SMA指标的数值会随着最新价格数据的更新而变化，它反映了一定周期内的价格平均水平。SMA线可以直观地显示价格走势的平滑趋势，并且可以帮助交易者更清晰地识别价格的趋势。
 *
 * SMA指标通常用于制定交易策略中的买卖信号。例如，当价格上穿SMA线时，可能暗示着价格的上升趋势开始，为买入信号；相反，当价格下穿SMA线时，可能暗示着价格的下跌趋势开始，为卖出信号。
 *
 * 总的来说，SMA指标是一种简单但有效的技术指标，常用于平滑价格数据并识别价格趋势，可以帮助交易者更好地理解市场的价格动态并制定相应的交易策略。
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/s/sma.asp">https://www.investopedia.com/terms/s/sma.asp</a>
 */
public class SMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;

    private final int barCount;

    public SMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        Num sum = numOf(0);
        for (int i = Math.max(0, index - barCount + 1); i <= index; i++) {
            sum = sum.plus(indicator.getValue(i));
        }

        final int realBarCount = Math.min(barCount, index + 1);
        return sum.dividedBy(numOf(realBarCount));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}
