/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.MMAIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Average true range indicator.
 */
public class ATRIndicator extends AbstractIndicator<Num> {

    private final TRIndicator trIndicator;
    private final transient MMAIndicator averageTrueRangeIndicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public ATRIndicator(BarSeries series, int barCount) {
        this(new TRIndicator(series), barCount);
    }

    /**
     * Constructor.
     *
     * @param tr       the {@link TRIndicator}
     * @param barCount the time frame
     */
    public ATRIndicator(TRIndicator tr, int barCount) {
        super(tr.getBarSeries());
        this.trIndicator = tr;
        this.barCount = barCount;
        this.averageTrueRangeIndicator = new MMAIndicator(tr, barCount);
    }

    @Override
    public Num getValue(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        return averageTrueRangeIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return trIndicator.getCountOfUnstableBars() + getBarCount();
    }

    /** @return the {@link #trIndicator} */
    public TRIndicator getTRIndicator() {
        return trIndicator;
    }

    /** @return the bar count of {@link #averageTrueRangeIndicator} */
    public int getBarCount() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + getBarCount();
    }
}
