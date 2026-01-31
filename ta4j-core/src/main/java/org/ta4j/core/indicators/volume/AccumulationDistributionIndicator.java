/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.helpers.CloseLocationValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Accumulation-distribution indicator.
 */
public class AccumulationDistributionIndicator extends RecursiveCachedIndicator<Num> {

    private final CloseLocationValueIndicator clvIndicator;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public AccumulationDistributionIndicator(BarSeries series) {
        super(series);
        this.clvIndicator = new CloseLocationValueIndicator(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return getBarSeries().numFactory().zero();
        }

        // Calculating the money flow multiplier
        Num moneyFlowMultiplier = clvIndicator.getValue(index);

        // Calculating the money flow volume
        Num moneyFlowVolume = moneyFlowMultiplier.multipliedBy(getBarSeries().getBar(index).getVolume());

        return moneyFlowVolume.plus(getValue(index - 1));
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
