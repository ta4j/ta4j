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
public class BollingerBandsUpperIndicator extends CachedIndicator<Num> {

    private final BollingerBandsMiddleIndicator bbm;
    private final Indicator<Num> deviation;
    private final Num k;

    /**
     * Constructor with {@code k} = 2.
     *
     * @param bbm       the middle band Indicator. Typically an {@code SMAIndicator}
     *                  is used.
     * @param deviation the deviation above and below the middle, factored by k.
     *                  Typically a {@code StandardDeviationIndicator} is used.
     */
    public BollingerBandsUpperIndicator(BollingerBandsMiddleIndicator bbm, Indicator<Num> deviation) {
        this(bbm, deviation, bbm.getBarSeries().numFactory().two());
    }

    /**
     * Constructor.
     *
     * @param bbm       the middle band Indicator. Typically an {@code SMAIndicator}
     *                  is used.
     * @param deviation the deviation above and below the middle, factored by k.
     *                  Typically a {@code StandardDeviationIndicator} is used.
     * @param k         the scaling factor to multiply the deviation by. Typically
     *                  2.
     */
    public BollingerBandsUpperIndicator(BollingerBandsMiddleIndicator bbm, Indicator<Num> deviation, Num k) {
        super(deviation);
        this.bbm = bbm;
        this.deviation = deviation;
        this.k = k;
    }

    @Override
    protected Num calculate(int index) {
        return bbm.getValue(index).plus(deviation.getValue(index).multipliedBy(k));
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(bbm.getCountOfUnstableBars(), deviation.getCountOfUnstableBars());
    }

    /** @return the K multiplier */
    public Num getK() {
        return k;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "k: " + k + "deviation: " + deviation + "series" + bbm;
    }
}
