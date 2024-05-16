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
 * Zero-lag exponential moving average indicator.
 * 零滞后指数移动平均线指标。
 *
 * 零滞后指数移动平均线（Zero-lag Exponential Moving Average，ZLEMA）是一种技术指标，旨在减少指数移动平均线（EMA）的滞后效应，更快地反映价格的变化。
 *
 * 与传统的指数移动平均线不同，零滞后指数移动平均线尝试通过减少滞后来更快地跟踪价格的趋势。它的计算方法比较复杂，但基本上可以通过以下步骤来实现：
 *
 * 1. 计算传统的指数移动平均线（EMA）。
 * 2. 计算当前价格与传统EMA之间的偏差（即残差）。
 * 3. 将这个偏差添加到EMA中，以消除滞后。
 *
 * 数学上，ZLEMA的计算公式如下：
 *
 *   ZLEMA =  EMA}(C) +  EMA(C - EMA(C))
 *
 * 其中：
 * -  C  是当前的收盘价。
 * -  EMA (x)  是对输入数据 x 进行指数移动平均的函数。
 *
 * ZLEMA指标的数值通常以一条平滑的曲线显示在价格图上，相比于传统的EMA，ZLEMA更接近价格的变化，更及时地反映价格的趋势。
 *
 * 交易者通常使用ZLEMA指标来捕捉价格的短期和长期趋势，并据此制定买卖策略。例如，当价格上穿ZLEMA线时，可能暗示着价格的上升趋势开始，为买入信号；相反，当价格下穿ZLEMA线时，可能暗示着价格的下跌趋势开始，为卖出信号。
 *
 * 总的来说，零滞后指数移动平均线是一种旨在减少滞后效应的技术指标，可以帮助交易者更快地捕捉价格的变化并制定相应的交易策略。
 *
 *
 * @see <a href=
 *      "http://www.fmlabs.com/reference/default.htm?url=ZeroLagExpMA.htm">
 *      http://www.fmlabs.com/reference/default.htm?url=ZeroLagExpMA.htm</a>
 */
public class ZLEMAIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final Num k;
    private final int lag;

    public ZLEMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        k = numOf(2).dividedBy(numOf(barCount + 1));
        lag = (barCount - 1) / 2;
    }

    @Override
    protected Num calculate(int index) {
        if (index + 1 < barCount) {
            // Starting point of the ZLEMA
            // ZLEMA 的起点
            return new SMAIndicator(indicator, barCount).getValue(index);
        }
        if (index == 0) {
            // If the barCount is bigger than the indicator's value count
            // 如果 barCount 大于指标的值计数
            return indicator.getValue(0);
        }
        Num zlemaPrev = getValue(index - 1);
        return k.multipliedBy(numOf(2).multipliedBy(indicator.getValue(index)).minus(indicator.getValue(index - lag)))
                .plus(numOf(1).minus(k).multipliedBy(zlemaPrev));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
