/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
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
    private final DifferenceIndicator closePriceDifference;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public TimeSegmentedVolumeIndicator(BarSeries series, int barCount) {
        super(series);

        this.closePriceDifference = new DifferenceIndicator(new ClosePriceIndicator(series));
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        // If the index is less than the required unstable bars, return NaN
        if (index < this.getCountOfUnstableBars()) {
            return NaN;
        }

        Num tsv = getBarSeries().numFactory().zero();

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
    public int getCountOfUnstableBars() {
        return closePriceDifference.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
