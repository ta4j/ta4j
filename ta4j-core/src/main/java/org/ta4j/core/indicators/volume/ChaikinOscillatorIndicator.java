/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Chaikin Oscillator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_oscillator">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_oscillator</a>
 */
public class ChaikinOscillatorIndicator extends CachedIndicator<Num> {

    private final EMAIndicator emaShort;
    private final EMAIndicator emaLong;

    /**
     * Constructor.
     *
     * @param series        the {@link BarSeries}
     * @param shortBarCount the bar count for {@link #emaShort} (usually 3)
     * @param longBarCount  the bar count for {@link #emaLong} (usually 10)
     */
    public ChaikinOscillatorIndicator(BarSeries series, int shortBarCount, int longBarCount) {
        super(series);
        this.emaShort = new EMAIndicator(new AccumulationDistributionIndicator(series), shortBarCount);
        this.emaLong = new EMAIndicator(new AccumulationDistributionIndicator(series), longBarCount);
    }

    /**
     * Constructor with {@code shortBarCount} = 3 and {@code longBarCount} = 10.
     *
     * @param series the {@link BarSeries}
     */
    public ChaikinOscillatorIndicator(BarSeries series) {
        this(series, 3, 10);
    }

    @Override
    protected Num calculate(int index) {
        return emaShort.getValue(index).minus(emaLong.getValue(index));
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }
}
