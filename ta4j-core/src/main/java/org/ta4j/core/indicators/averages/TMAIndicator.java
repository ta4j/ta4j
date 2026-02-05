/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Triangular Moving Average (TMA) Indicator.
 *
 * TMA is a double-smoothing of the Simple Moving Average (SMA).
 *
 * Triangular Moving Average (TMA) is a type of moving average that places
 * greater emphasis on the central portion of the data series. It is calculated
 * as a second smoothing of a Simple Moving Average (SMA), making it smoother
 * than the SMA or Exponential Moving Average (EMA). Due to its double-smoothing
 * nature, the TMA is commonly used to identify long-term trends in financial
 * markets, reducing noise from short-term fluctuations.
 */
public class TMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final SMAIndicator sma;
    private final SMAIndicator smaSma;

    /**
     * Constructor.
     *
     * @param indicator an indicator
     * @param barCount  the Simple Moving Average time frame
     */
    public TMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator.getBarSeries());
        this.barCount = barCount;
        this.sma = new SMAIndicator(indicator, barCount);
        this.smaSma = new SMAIndicator(sma, barCount);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        return smaSma.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return smaSma.getCountOfUnstableBars();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
