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
import eu.verdelhan.ta4j.indicators.helpers.DirectionalDownIndicator;
import eu.verdelhan.ta4j.indicators.helpers.DirectionalUpIndicator;

/**
 * Directional movement indicator.
 * <p>
 */
public class DirectionalMovementIndicator extends CachedIndicator<Decimal>{

    private final int timeFrame;
    private final DirectionalUpIndicator dup;
    private final DirectionalDownIndicator ddown;

    public DirectionalMovementIndicator(TimeSeries series, int timeFrame) {
        super(series);
        this.timeFrame = timeFrame;
        dup = new DirectionalUpIndicator(series, timeFrame);
        ddown = new DirectionalDownIndicator(series, timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        Decimal dupValue = dup.getValue(index);
        Decimal ddownValue = ddown.getValue(index);
        Decimal difference = dupValue.minus(ddownValue);
        return difference.abs().dividedBy(dupValue.plus(ddownValue)).multipliedBy(Decimal.HUNDRED);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " timeFrame: " + timeFrame;
    }
}
