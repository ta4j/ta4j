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
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Acceleration-deceleration indicator.
 * 加速-减速指示器。
 *
 * 加速/减速指标（Acceleration/Deceleration，AC）是由比尔·威廉姆斯（Bill Williams）创造的一种技术指标，也被称为“加速/减速震荡器”（Accelerator/Decelerator Oscillator）。它用于衡量价格动能的加速度和减速度，从而帮助交易者确认价格趋势的强度和可能的反转点。
 *
 * AC指标的计算基于Awesome Oscillator（AO），它是通过计算简单移动平均线（SMA）和指数移动平均线（EMA）之间的差异来确定的。AO指标的计算公式如下：
 *
 *   AO = SMA(5, High) - SMA(34, High)
 *
 * 然后，AC指标是AO指标与34期SMA的差异：
 *
 *   AC = AO - SMA(5, AO)
 *
 * 在AC指标中，如果当前柱子的值大于前一个柱子的值，则表示加速度正在增加，价格的上涨趋势可能会加速；相反，如果当前柱子的值小于前一个柱子的值，则表示减速度正在增加，价格的上涨趋势可能会减缓。
 *
 * AC指标的变化可以帮助交易者确认价格趋势的强度和可能的反转点。例如，当价格上涨时，如果AC指标的值开始下降，则可能暗示着价格上涨的动力正在减弱，可能发生价格的反转。因此，交易者可以结合AC指标和其他技术分析工具，来制定交易策略。
 *
 */
public class AccelerationDecelerationIndicator extends CachedIndicator<Num> {

    private final AwesomeOscillatorIndicator awesome;
    private final SMAIndicator sma;

    public AccelerationDecelerationIndicator(BarSeries series, int barCountSma1, int barCountSma2) {
        super(series);
        this.awesome = new AwesomeOscillatorIndicator(new MedianPriceIndicator(series), barCountSma1, barCountSma2);
        this.sma = new SMAIndicator(awesome, barCountSma1);
    }

    public AccelerationDecelerationIndicator(BarSeries series) {
        this(series, 5, 34);
    }

    @Override
    protected Num calculate(int index) {
        return awesome.getValue(index).minus(sma.getValue(index));
    }
}
