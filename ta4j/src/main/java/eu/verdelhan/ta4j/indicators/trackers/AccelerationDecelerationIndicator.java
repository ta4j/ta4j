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
package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.oscillators.AwesomeOscillatorIndicator;
import eu.verdelhan.ta4j.indicators.simple.MedianPriceIndicator;

/**
 * Acceleration-deceleration indicator.
 * <p>
 */
public class AccelerationDecelerationIndicator extends CachedIndicator<Decimal> {
    
    private AwesomeOscillatorIndicator awesome;
    
    private SMAIndicator sma5;

    public AccelerationDecelerationIndicator(TimeSeries series, int timeFrameSma1, int timeFrameSma2) {
        super(series);
        this.awesome = new AwesomeOscillatorIndicator(new MedianPriceIndicator(series), timeFrameSma1, timeFrameSma2);
        this.sma5 = new SMAIndicator(awesome, timeFrameSma1);
    }
    
    public AccelerationDecelerationIndicator(TimeSeries series) {
        this(series, 5, 34);
    }
    
    @Override
    protected Decimal calculate(int index) {
        return awesome.getValue(index).minus(sma5.getValue(index));
    }
}
