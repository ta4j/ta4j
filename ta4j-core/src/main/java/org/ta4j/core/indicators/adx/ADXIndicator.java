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
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.MMAIndicator;
import org.ta4j.core.num.Num;

/**
 * ADX indicator.
 * 
 * <p>
 * Part of the Directional Movement System.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/a/adx.asp">https://www.investopedia.com/terms/a/adx.asp</a>
 */
public class ADXIndicator extends AbstractIndicator<Num> {

    private final int diBarCount;
    private final int adxBarCount;
    private final MMAIndicator averageDXIndicator;

    /**
     * Constructor.
     * 
     * @param series      the bar series
     * @param diBarCount  the bar count for {@link DXIndicator}
     * @param adxBarCount the bar count for {@link #averageDXIndicator}
     */
    public ADXIndicator(BarSeries series, int diBarCount, int adxBarCount) {
        super(series);
        this.diBarCount = diBarCount;
        this.adxBarCount = adxBarCount;
        this.averageDXIndicator = new MMAIndicator(new DXIndicator(series, diBarCount), adxBarCount);
    }

    /**
     * Constructor.
     * 
     * @param series   the bar series
     * @param barCount the bar count for {@link DXIndicator} and
     *                 {@link #averageDXIndicator}
     */
    public ADXIndicator(BarSeries series, int barCount) {
        this(series, barCount, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return averageDXIndicator.getValue(index);
    }

    @Override
    public int getUnstableBars() {
        return Math.max(diBarCount, adxBarCount);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " diBarCount: " + diBarCount + " adxBarCount: " + adxBarCount;
    }
}
