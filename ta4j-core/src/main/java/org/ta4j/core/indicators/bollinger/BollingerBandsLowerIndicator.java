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
public class BollingerBandsLowerIndicator extends CachedIndicator<Num> {

    private final BollingerBandsMiddleIndicator bbm;
    private final Indicator<Num> indicator;
    private final Num k;

    /**
     * Constructor with {@code k} = 2.
     *
     * @param bbm       the middle band Indicator. Typically an {@code SMAIndicator}
     *                  is used.
     * @param indicator the deviation above and below the middle, factored by k.
     *                  Typically a {@code StandardDeviationIndicator} is used.
     */
    public BollingerBandsLowerIndicator(BollingerBandsMiddleIndicator bbm, Indicator<Num> indicator) {
        this(bbm, indicator, bbm.getBarSeries().numFactory().numOf(2));
    }

    /**
     * Constructor.
     *
     * @param bbm       the middle band Indicator. Typically an {@code SMAIndicator}
     *                  is used.
     * @param indicator the deviation above and below the middle, factored by k.
     *                  Typically a {@code StandardDeviationIndicator} is used.
     * @param k         the scaling factor to multiply the deviation by. Typically
     *                  2.
     */
    public BollingerBandsLowerIndicator(BollingerBandsMiddleIndicator bbm, Indicator<Num> indicator, Num k) {
        super(indicator);
        this.bbm = bbm;
        this.indicator = indicator;
        this.k = k;
    }

    @Override
    protected Num calculate(int index) {
        return bbm.getValue(index).minus(indicator.getValue(index).multipliedBy(k));
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    /** @return the K multiplier */
    public Num getK() {
        return k;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "k: " + k + "deviation: " + indicator + "series: " + bbm;
    }
}
