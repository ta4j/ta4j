/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

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
package org.ta4j.core.indicators;


import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;

/**
 * Aroon Oscillator.
 * <p></p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon_oscillator">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon_oscillator</a>
 */
public class AroonOscillatorIndicator extends CachedIndicator<Decimal>{

    private final AroonDownIndicator aroonDownIndicator;
    private final AroonUpIndicator aroonUpIndicator;
    private final int timeFrame;

    public AroonOscillatorIndicator(TimeSeries series, int timeFrame) {
        super(series);
        this.timeFrame = timeFrame;
        aroonDownIndicator = new AroonDownIndicator(series, timeFrame);
        aroonUpIndicator = new AroonUpIndicator(series, timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        return aroonUpIndicator.getValue(index).minus(aroonDownIndicator.getValue(index));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+" timeFrame: "+timeFrame;
    }
}
