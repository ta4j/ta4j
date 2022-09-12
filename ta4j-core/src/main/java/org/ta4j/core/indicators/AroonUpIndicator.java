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

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Aroon up indicator.
 * * 阿隆向上指标。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon">chart_school:technical_indicators:aroon</a>
 */
public class AroonUpIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final HighestValueIndicator highestHighPriceIndicator;
    private final Indicator<Num> highPriceIndicator;
    private final Num hundred;

    /**
     * Constructor.
     *
     * @param highPriceIndicator the indicator for the high price (default  {@link HighPriceIndicator})
     *                           * @param highPriceIndicator 高价指标（默认 {@link HighPriceIndicator}）
     * @param barCount           the time frame 時間範圍
     */
    public AroonUpIndicator(Indicator<Num> highPriceIndicator, int barCount) {
        super(highPriceIndicator);
        this.barCount = barCount;
        this.highPriceIndicator = highPriceIndicator;
        this.hundred = numOf(100);
        // + 1 needed for last possible iteration in loop
        // + 1 循环中最后一次可能的迭代需要
        this.highestHighPriceIndicator = new HighestValueIndicator(highPriceIndicator, barCount + 1);
    }

    /**
     * Default Constructor that is using the high price
     * 使用高价的默认构造函数
     *
     * @param series   the bar series
     *                 酒吧系列
     * @param barCount the time frame
     *                 时间范围
     */
    public AroonUpIndicator(BarSeries series, int barCount) {
        this(new HighPriceIndicator(series), barCount);
    }

    @Override
    protected Num calculate(int index) {
        if (getBarSeries().getBar(index).getHighPrice().isNaN())
            return NaN;

        // Getting the number of bars since the highest close price
        // 获取自最高收盘价以来的柱数
        int endIndex = Math.max(0, index - barCount);
        int nbBars = 0;
        for (int i = index; i > endIndex; i--) {
            if (highPriceIndicator.getValue(i).isEqual(highestHighPriceIndicator.getValue(index))) {
                break;
            }
            nbBars++;
        }

        return numOf(barCount - nbBars).dividedBy(numOf(barCount)).multipliedBy(hundred);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
