/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
 * authors (see AUTHORS)
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
package org.ta4j.core.indicators.adx;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * DX indicator.
 * 
 * <p>
 * Part of the Directional Movement System.
 */
public class DXIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final PlusDIIndicator plusDIIndicator;
    private final MinusDIIndicator minusDIIndicator;

    /**
     * Constructor.
     * 
     * @param series   the bar series
     * @param barCount the bar count for {@link #plusDIIndicator} and
     *                 {@link #minusDIIndicator}
     */
    public DXIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.plusDIIndicator = new PlusDIIndicator(series, barCount);
        this.minusDIIndicator = new MinusDIIndicator(series, barCount);
    }

    @Override
    protected Num calculate(int index) {
        Num pdiValue = plusDIIndicator.getValue(index);
        Num mdiValue = minusDIIndicator.getValue(index);
        if (pdiValue.plus(mdiValue).equals(zero())) {
            return zero();
        }
        return pdiValue.minus(mdiValue).abs().dividedBy(pdiValue.plus(mdiValue)).multipliedBy(hundred());
    }

    @Override
    public int getUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
