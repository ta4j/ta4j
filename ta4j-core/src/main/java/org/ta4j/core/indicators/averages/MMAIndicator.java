/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Modified moving average indicator.
 *
 * <p>
 * It is similar to exponential moving average but smooths more slowly. Used in
 * Welles Wilder's indicators like ADX, RSI.
 */
public class MMAIndicator extends AbstractEMAIndicator {

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the MMA time frame
     */
    public MMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator, barCount, 1.0 / barCount);
    }

    @Override
    public int getCountOfUnstableBars() {
        return getBarCount();
    }
}
