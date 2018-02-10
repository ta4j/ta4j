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
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * On-balance volume indicator.
 * <p></p>
 */
public class OnBalanceVolumeIndicator extends RecursiveCachedIndicator<Num> {

    private static final long serialVersionUID = -5870953997596403170L;
    private final TimeSeries series;

    public OnBalanceVolumeIndicator(TimeSeries series) {
        super(series);
        this.series = series;
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return numOf(0);
        }
        Num yesterdayClose = series.getBar(index - 1).getClosePrice();
        Num todayClose = series.getBar(index).getClosePrice();

        if (yesterdayClose.isGreaterThan(todayClose)) {
            return getValue(index - 1).minus(series.getBar(index).getVolume());
        } else if (yesterdayClose.isLessThan(todayClose)) {
            return getValue(index - 1).plus(series.getBar(index).getVolume());
        }
        return getValue(index - 1);
    }
}
