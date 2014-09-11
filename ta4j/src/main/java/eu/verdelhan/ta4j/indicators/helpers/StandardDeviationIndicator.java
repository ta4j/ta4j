/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;

/**
 * Standard deviation indicator.
 * <p>
 */
public class StandardDeviationIndicator implements Indicator<Double> {

    private Indicator<? extends Number> indicator;

    private int timeFrame;

    private SMAIndicator sma;

    /**
     * Constructor.
     * @param indicator the indicator
     * @param timeFrame the time frame
     */
    public StandardDeviationIndicator(Indicator<? extends Number> indicator, int timeFrame) {
        this.indicator = indicator;
        this.timeFrame = timeFrame;
        sma = new SMAIndicator(indicator, timeFrame);
    }

    @Override
    public Double getValue(int index) {
        double standardDeviation = 0.0;
        double average = sma.getValue(index);
        for (int i = Math.max(0, index - timeFrame + 1); i <= index; i++) {
            standardDeviation += Math.pow(indicator.getValue(i).doubleValue() - average, 2.0);
        }
        return Math.sqrt(standardDeviation);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " timeFrame: " + timeFrame;
    }
}
