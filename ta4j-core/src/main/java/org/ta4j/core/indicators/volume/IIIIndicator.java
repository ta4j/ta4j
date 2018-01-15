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
package org.ta4j.core.indicators.volume;

import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.*;

/**
 * Intraday Intensity Index
 * @see <a https://www.investopedia.com/terms/i/intradayintensityindex.asp>
 *     https://www.investopedia.com/terms/i/intradayintensityindex.asp</a>
 */
public class IIIIndicator extends CachedIndicator<Decimal> {


    private ClosePriceIndicator closePriceIndicator;

    private MaxPriceIndicator maxPriceIndicator;

    private MinPriceIndicator minPriceIndicator;

    private VolumeIndicator volumeIndicator;

    public IIIIndicator(TimeSeries series) {
        super(series);
        closePriceIndicator = new ClosePriceIndicator(series);
        maxPriceIndicator = new MaxPriceIndicator(series);
        minPriceIndicator = new MinPriceIndicator(series);
        volumeIndicator = new VolumeIndicator(series);
    }
    @Override
    protected Decimal calculate(int index) {

        if (index == getTimeSeries().getBeginIndex()) {
            return Decimal.ZERO;
        }
        Decimal doubleClosePrice =  Decimal.valueOf(2).multipliedBy(closePriceIndicator.getValue(index));
        Decimal highmlow = maxPriceIndicator.getValue(index).minus(minPriceIndicator.getValue(index));
        Decimal highplow = maxPriceIndicator.getValue(index).plus(minPriceIndicator.getValue(index));

        return (doubleClosePrice.minus(highplow)).dividedBy(highmlow.multipliedBy(volumeIndicator.getValue(index)));

    }
}
