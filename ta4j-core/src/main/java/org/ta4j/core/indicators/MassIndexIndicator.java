/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;

/**
 * Mass index indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:mass_index">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:mass_index</a>
 */
public class MassIndexIndicator extends CachedIndicator<Num> {

    private final transient EMAIndicator singleEma;
    private final transient EMAIndicator doubleEma;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series      the bar series
     * @param emaBarCount the time frame for EMAs (usually 9)
     * @param barCount    the time frame
     */
    public MassIndexIndicator(BarSeries series, int emaBarCount, int barCount) {
        super(series);
        final var highLowDifferential = BinaryOperationIndicator.difference(new HighPriceIndicator(series),
                new LowPriceIndicator(series));
        this.barCount = barCount;
        this.singleEma = new EMAIndicator(highLowDifferential, emaBarCount);
        this.doubleEma = new EMAIndicator(singleEma, emaBarCount); // Not the same formula as DoubleEMAIndicator
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        Num massIndex = getBarSeries().numFactory().zero();
        for (int i = startIndex; i <= index; i++) {
            Num emaRatio = singleEma.getValue(i).dividedBy(doubleEma.getValue(i));
            massIndex = massIndex.plus(emaRatio);
        }
        return massIndex;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
