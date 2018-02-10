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
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * True range indicator.
 * <p/>
 */
public class TRIndicator extends CachedIndicator<Num> {

    private final TimeSeries series;

    public TRIndicator(TimeSeries series) {
        super(series);
        this.series = series;
    }

    @Override
    protected Num calculate(int index) {
        Num ts = series.getBar(index).getMaxPrice().minus(series.getBar(index).getMinPrice());
        Num ys = index == 0 ? numOf(0) : series.getBar(index).getMaxPrice().minus(series.getBar(index - 1).getClosePrice());
        Num yst = index == 0 ? numOf(0) : series.getBar(index - 1).getClosePrice().minus(series.getBar(index).getMinPrice());
        return ts.abs().max(ys.abs()).max(yst.abs());
    }
}
