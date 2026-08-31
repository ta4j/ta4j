/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Exponential Displaced Moving Average (EDMA)
 *
 * Exponential Displaced Moving Average (EDMA) is a variation of the traditional
 * Exponential Moving Average (EMA) where the calculated average is shifted in
 * time by a specified number of periods. This displacement enhances trend
 * visualization and provides traders with an adjusted perspective of price
 * action, either anticipating future price movements or analyzing past trends.
 *
 */
public class EDMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final int displacement;
    private final transient EMAIndicator ema;
    private final transient int unstableBars;

    /**
     * Constructor.
     *
     * @param indicator    an indicator
     * @param barCount     the Exponential Moving Average time frame
     * @param displacement the Exponential Moving Average displacement
     */
    public EDMAIndicator(Indicator<Num> indicator, int barCount, int displacement) {
        // The source is registered so a rebaselining input (e.g., a stochastic)
        // invalidates the whole displaced cache after a series head advance.
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.displacement = displacement;
        this.ema = new EMAIndicator(indicator, barCount);
        int emaUnstableBars = indicator.getCountOfUnstableBars() + barCount;
        this.unstableBars = Math.max(0, emaUnstableBars + displacement);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        int displacedIndex = index - displacement;
        if (index <= displacement) {
            return ema.getValue(0);
        }
        return ema.getValue(displacedIndex);
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " displacement: " + displacement;
    }
}
