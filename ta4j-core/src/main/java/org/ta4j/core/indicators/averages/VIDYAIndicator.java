/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CMOIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Chande's Variable Index Dynamic Moving Average (VIDYA) Indicator
 *
 * Chande’s Variable Index Dynamic Moving Average (VIDYA) is a sophisticated
 * moving average developed by Tushar Chande. Unlike traditional moving
 * averages, VIDYA adapts to market volatility by incorporating the Chande
 * Momentum Oscillator (CMO) to dynamically adjust its smoothing factor. This
 * results in a highly responsive yet stable moving average that is particularly
 * useful in dynamic market conditions.
 *
 */
public class VIDYAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator; // Input price (e.g., close price)
    private final int cmoPeriod; // Lookback period for cmoPeriod
    private final int vidyaPeriod; // Lookback period for VIDYA
    private final CMOIndicator cmoIndicator;

    /**
     * Constructor.
     *
     * @param indicator   Input price indicator (e.g., ClosePriceIndicator).
     * @param cmoPeriod   Lookback period for the Chande Momentum Oscillator.
     * @param vidyaPeriod Lookback period for VIDYA.
     */
    public VIDYAIndicator(Indicator<Num> indicator, int cmoPeriod, int vidyaPeriod) {
        super(indicator.getBarSeries());
        this.indicator = indicator;
        this.cmoPeriod = cmoPeriod;
        this.vidyaPeriod = vidyaPeriod;
        this.cmoIndicator = new CMOIndicator(indicator, cmoPeriod);

    }

    @Override
    protected Num calculate(int index) {
        if (index < 1) {
            // Initialize VIDYA with the first price
            return indicator.getValue(index);
        }

        // Calculate the Chande Momentum Oscillator (CMO)
        Num cmo = cmoIndicator.getValue(index);

        // Normalize the CMO to derive the smoothing constant
        Num smoothingConstant = cmo.abs().dividedBy(indicator.getBarSeries().numFactory().hundred());

        // Calculate VIDYA using the smoothing constant
        Num previousVIDYA = getValue(index - 1);
        Num currentPrice = indicator.getValue(index);
        return previousVIDYA.plus(smoothingConstant.multipliedBy(currentPrice.minus(previousVIDYA)));
    }

    @Override
    public int getCountOfUnstableBars() {
        return cmoIndicator.getCountOfUnstableBars();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " cmoPeriod: " + cmoPeriod + " vidyaPeriod: " + vidyaPeriod;
    }

}
