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
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceDifferenceIndicator;
import org.ta4j.core.num.Num;

/**
 * Time Segmented Volume (TSV) indicator.
 *
 * This class calculates the Time Segmented Volume (TSV). TSV is a volume-based
 * indicator that gauges the supply and demand for a security based on the price
 * changes and volume of each period (or "segment") in the given time frame.
 *
 * The calculation involves multiplying the volume of each period by the
 * difference between the close price and the open price, and summing up these
 * values over the given period.
 *
 * @see <a href="https://www.investopedia.com/terms/t/tsv.asp">Time Segmented
 *      Volume (TSV)</a>
 */
public class TimeSegmentedVolumeIndicator extends CachedIndicator<Num> {
    private final ClosePriceDifferenceIndicator closePriceDifference;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public TimeSegmentedVolumeIndicator(BarSeries series, int barCount) {
        super(series);

        this.closePriceDifference = new ClosePriceDifferenceIndicator(series);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        // If the index is less than the required unstable bars, return NaN
        if (index < this.getUnstableBars()) {
            return NaN;
        }

        Num tsv = zero();

        // Calculate the TSV for the given period
        int startIndex = Math.max(0, index - barCount + 1);
        for (int i = startIndex; i <= index; i++) {
            Num closePriceDifferenceValue = closePriceDifference.getValue(i);
            Num currentVolume = getBarSeries().getBar(i).getVolume();

            tsv = tsv.plus(closePriceDifferenceValue.multipliedBy(currentVolume));
        }

        return tsv;
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
