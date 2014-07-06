/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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
package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;

/**
 * Awesome oscillator. (AO)
 * <p>
 * @see http://www.forexgurus.co.uk/indicators/awesome-oscillator
 */
public class AwesomeOscillatorIndicator implements Indicator<Double> {

    private SMAIndicator sma5;

    private SMAIndicator sma34;

    public AwesomeOscillatorIndicator(Indicator<? extends Number> average, int timeFrameSma1, int timeFrameSma2) {
        this.sma5 = new SMAIndicator(average, timeFrameSma1);
        this.sma34 = new SMAIndicator(average, timeFrameSma2);
    }

    public AwesomeOscillatorIndicator(Indicator<? extends Number> average) {
        this.sma5 = new SMAIndicator(average, 5);
        this.sma34 = new SMAIndicator(average, 34);
    }

    @Override
    public Double getValue(int index) {
        return sma5.getValue(index) - sma34.getValue(index);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
