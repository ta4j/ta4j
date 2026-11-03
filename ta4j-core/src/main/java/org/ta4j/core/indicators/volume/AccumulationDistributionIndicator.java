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
 *
 * <p>
 * A live instance carries accumulated money flow across retained-head advances,
 * including one-bar windows. Without a cached predecessor it starts from the
 * retained bar's money flow; index zero retains its zero baseline.
 * </p>
 */
public class AccumulationDistributionIndicator extends RecursiveCachedIndicator<Num> {

    private final transient CloseLocationValueIndicator clvIndicator;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public AccumulationDistributionIndicator(BarSeries series) {
        this(new CloseLocationValueIndicator(series));
    }

    private AccumulationDistributionIndicator(CloseLocationValueIndicator clvIndicator) {
        super(clvIndicator);
        this.clvIndicator = clvIndicator;
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

        Num previous = index == getBarSeries().getBeginIndex() ? getCachedValueOrNull(index - 1) : getValue(index - 1);
        return moneyFlowVolume.plus(previous == null ? getBarSeries().numFactory().zero() : previous);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
