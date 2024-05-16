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
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Ulcer index indicator.
 * 溃疡指数指标。
 *
 * 溃疡指数（Ulcer Index）是一种技术指标，用于衡量资产价格下跌的幅度和持续时间，以评估价格波动的风险程度。
 *
 * 溃疡指数的计算基于资产价格相对于过去某一段时间内价格最高值的跌幅平方的平均值的开平方。通常情况下，溃疡指数的计算过程如下：
 *
 * 1. 首先确定所选时间段内的最高价。
 * 2. 计算每个交易日的价格相对于最高价的跌幅的平方。
 * 3. 对所有跌幅的平方值进行平均，并计算其开平方，得到溃疡指数的数值。
 *
 * 溃疡指数的数值通常在0以上，数值越高表示价格下跌的幅度和持续时间越大，风险程度越高。
 *
 * 交易者通常使用溃疡指数来评估资产价格的波动性和风险程度。较高的溃疡指数可能意味着价格波动较大，市场风险较高，而较低的溃疡指数则可能表示价格波动较小，市场风险较低。
 *
 * 总的来说，溃疡指数是一种用于评估资产价格波动风险程度的技术指标，可以帮助交易者更好地理解市场的价格动态并制定相应的交易策略。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ulcer_index">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ulcer_index</a>
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Ulcer_index">https://en.wikipedia.org/wiki/Ulcer_index</a>
 */
public class UlcerIndexIndicator extends CachedIndicator<Num> {

    private Indicator<Num> indicator;

    private HighestValueIndicator highestValueInd;

    private int barCount;

    /**
     * Constructor.
     *
     * @param indicator the indicator 指標
     * @param barCount  the time frame 時間範圍
     */
    public UlcerIndexIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        highestValueInd = new HighestValueIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        Num squaredAverage = numOf(0);
        for (int i = startIndex; i <= index; i++) {
            Num currentValue = indicator.getValue(i);
            Num highestValue = highestValueInd.getValue(i);
            Num percentageDrawdown = currentValue.minus(highestValue).dividedBy(highestValue).multipliedBy(numOf(100));
            squaredAverage = squaredAverage.plus(percentageDrawdown.pow(2));
        }
        squaredAverage = squaredAverage.dividedBy(numOf(numberOfObservations));
        return squaredAverage.sqrt();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
