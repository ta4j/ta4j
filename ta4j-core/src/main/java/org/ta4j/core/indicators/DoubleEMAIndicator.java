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
import org.ta4j.core.Indicator;

/**
 * Double exponential moving average indicator.
 * </p/>
 * see https://en.wikipedia.org/wiki/Double_exponential_moving_average
 */
public class DoubleEMAIndicator extends CachedIndicator<Decimal> {

    private static final long serialVersionUID = 502597792760330884L;
	
    private final int timeFrame;
    private final EMAIndicator ema;
    private final EMAIndicator emaEma;

    /**
     * Constructor.
     * 
     * @param indicator the indicator
     * @param timeFrame the time frame
     */
    public DoubleEMAIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator);
        this.timeFrame = timeFrame;
        this.ema = new EMAIndicator(indicator, timeFrame);
        this.emaEma = new EMAIndicator(ema, timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        return ema.getValue(index).multipliedBy(Decimal.TWO)
                .minus(emaEma.getValue(index));
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + " timeFrame: " + timeFrame;
    }
}
