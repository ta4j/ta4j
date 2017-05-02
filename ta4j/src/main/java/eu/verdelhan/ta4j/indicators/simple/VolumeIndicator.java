/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

/**
 * Volume indicator.
 * <p>
 */
public class VolumeIndicator extends CachedIndicator<Decimal> {

    private TimeSeries series;

    private int timeFrame;
    
    public VolumeIndicator(TimeSeries series) {
        this(series, 1);
    }

    public VolumeIndicator(TimeSeries series, int timeFrame) {
        super(series);
        this.series = series;
        this.timeFrame = timeFrame;
    }

    @Override
    protected Decimal calculate(int index) {
        int startIndex = Math.max(0, index - timeFrame + 1);
        Decimal sumOfVolume = Decimal.ZERO;
        for (int i = startIndex; i <= index; i++) {
            sumOfVolume = sumOfVolume.plus(series.getTick(i).getVolume());
        }
        return sumOfVolume;
    }
}