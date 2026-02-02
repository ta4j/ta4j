/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.bollinger;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Buy - Occurs when the price line crosses from below to above the Lower
 * Bollinger Band.
 *
 * <p>
 * Sell - Occurs when the price line crosses from above to below the Upper
 * Bollinger Band.
 */
public class BollingerBandsMiddleIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator the indicator that gives the values of the middle band
     */
    public BollingerBandsMiddleIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {
        return indicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    public Indicator<Num> getIndicator() {
        return indicator;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " deviation: " + indicator;
    }
}
