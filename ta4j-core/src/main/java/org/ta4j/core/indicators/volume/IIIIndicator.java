/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization 
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core.indicators.volume;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Intraday Intensity Index
 * see https://www.investopedia.com/terms/i/intradayintensityindex.asp
 *     https://www.investopedia.com/terms/i/intradayintensityindex.asp
 */
public class IIIIndicator extends CachedIndicator<Num> {


    private ClosePriceIndicator closePriceIndicator;

    private HighPriceIndicator highPriceIndicator;

    private LowPriceIndicator lowPriceIndicator;

    private VolumeIndicator volumeIndicator;

    public IIIIndicator(TimeSeries series) {
        super(series);
        closePriceIndicator = new ClosePriceIndicator(series);
        highPriceIndicator = new HighPriceIndicator(series);
        lowPriceIndicator = new LowPriceIndicator(series);
        volumeIndicator = new VolumeIndicator(series);
    }
    @Override
    protected Num calculate(int index) {

        if (index == getTimeSeries().getBeginIndex()) {
            return numOf(0);
        }
        Num closePrice = numOf(2).multipliedBy(closePriceIndicator.getValue(index));
        Num highmlow = highPriceIndicator.getValue(index).minus(lowPriceIndicator.getValue(index));
        Num highplow = highPriceIndicator.getValue(index).plus(lowPriceIndicator.getValue(index));

        return (closePrice.minus(highplow)).dividedBy(highmlow.multipliedBy(volumeIndicator.getValue(index)));

    }
}
