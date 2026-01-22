/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Asymmetric Triangular Moving Average (ATMA) Indicator.
 *
 * ATMA is a double-smoothing of the Simple Moving Average (SMA), however unlike
 * the Triangular Moving Average (TMA) the modified equation creates a smoother
 * and slightly lagged moving average compared to the traditional TMA. By
 * varying the smoothing lengths asymmetrically, it balances responsiveness to
 * initial price changes with reduced noise in the final result.
 *
 */
public class ATMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final int slow;
    private final int fast;
    private final SMAIndicator sma;
    private final SMAIndicator smaSma;

    /**
     * Constructor.
     *
     * @param indicator an indicator
     * @param barCount  the Simple Moving Average time frame
     */
    public ATMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator.getBarSeries());
        this.barCount = barCount;
        this.fast = (int) (Math.ceil(barCount / 2));
        this.slow = (int) (Math.floor(barCount / 2) + 1);
        this.sma = new SMAIndicator(indicator, slow);
        this.smaSma = new SMAIndicator(sma, fast);
    }

    @Override
    protected Num calculate(int index) {

        return smaSma.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " fast: " + fast + " slow: " + slow;
    }
}
