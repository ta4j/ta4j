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
import org.ta4j.core.num.Num;

/**
 * Aroon Oscillator.
 * * 阿隆指标 振荡器。
 *
 * Aroon振荡器（Aroon Oscillator）是一种基于Aroon指标的技术指标，用于衡量价格趋势的强度和变化。它是由Tushar Chande开发的，旨在帮助交易者识别价格趋势的变化和可能的转折点。
 *
 * Aroon振荡器是Aroon Up指标和Aroon Down指标之间的差异。Aroon Up指标衡量了在一定时间周期内，价格创下新高点后经过的期间，而Aroon Down指标则衡量了价格创下新低点后经过的期间。因此，Aroon振荡器的计算公式如下：
 *
 * Aroon Oscillator  =  Aroon Up  - Aroon Down
 *
 * Aroon Oscillator的数值范围通常从-100到+100之间，其中：
 * - 当Aroon Oscillator为正值时，表示价格创新高点后经过的时间周期数多于价格创新低点后经过的时间周期数，可能暗示着价格处于上升趋势。
 * - 当Aroon Oscillator为负值时，表示价格创新低点后经过的时间周期数多于价格创新高点后经过的时间周期数，可能暗示着价格处于下降趋势。
 * - 当Aroon Oscillator接近零时，表示价格创新高点和新低点之间的时间周期数相似，可能暗示着价格处于横盘或无明显趋势的状态。
 *
 * 交易者通常使用Aroon振荡器来确认价格趋势的强度和方向，并结合其他技术指标一起使用，以辅助他们的交易决策。例如，当Aroon Oscillator从负值转变为正值时，可能暗示着价格趋势的转折点，可能发生价格上涨；反之，当Aroon Oscillator从正值转变为负值时，可能暗示着价格趋势的反转，可能发生价格下跌。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon_oscillator">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon_oscillator</a>
 */
public class AroonOscillatorIndicator extends CachedIndicator<Num> {

    private final AroonDownIndicator aroonDownIndicator;
    private final AroonUpIndicator aroonUpIndicator;
    private final int barCount;

    public AroonOscillatorIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.aroonDownIndicator = new AroonDownIndicator(series, barCount);
        this.aroonUpIndicator = new AroonUpIndicator(series, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return aroonUpIndicator.getValue(index).minus(aroonDownIndicator.getValue(index));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

    public AroonDownIndicator getAroonDownIndicator() {
        return aroonDownIndicator;
    }

    public AroonUpIndicator getAroonUpIndicator() {
        return aroonUpIndicator;
    }
}
