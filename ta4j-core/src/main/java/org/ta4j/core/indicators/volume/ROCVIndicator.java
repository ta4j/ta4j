/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Rate of change of volume (ROCVIndicator) indicator.
 * Aka. Momentum of Volume
 * <p></p>
 * The ROCVIndicator calculation compares the current volume with the volume "n" periods ago.
 */
public class ROCVIndicator extends CachedIndicator<Num> {

    private static final long serialVersionUID = 6366365574748347534L;

    private final TimeSeries series;
    private final int barCount;

    private final Num HUNDRED;

    /**
     * Constructor.
     *
     * @param series the time series
     * @param barCount the time frame
     */
    public ROCVIndicator(TimeSeries series, int barCount) {
        super(series);
        this.series = series;
        this.barCount = barCount;
        this.HUNDRED = numOf(100);
    }

    @Override
    protected Num calculate(int index) {
        int nIndex = Math.max(index - barCount, 0);
        Num nPeriodsAgoValue = series.getBar(nIndex).getVolume();
        Num currentValue = series.getBar(index).getVolume();
        return currentValue.minus(nPeriodsAgoValue)
                .dividedBy(nPeriodsAgoValue)
                .multipliedBy(HUNDRED);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
