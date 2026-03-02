/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Measures the absolute price premium/discount versus VWAP.
 * <p>
 * Uses the same VWAP window/anchor definition as the supplied VWAP indicator.
 *
 * @since 0.19
 */
public class VWAPDeviationIndicator extends CachedIndicator<Num> {

    @SuppressWarnings("unused")
    private final Indicator<Num> priceIndicator;
    private final AbstractVWAPIndicator vwapIndicator;
    private final transient Indicator<Num> difference;

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator (for example close price)
     * @param vwapIndicator  the VWAP indicator to compare against
     *
     * @since 0.19
     */
    public VWAPDeviationIndicator(Indicator<Num> priceIndicator, AbstractVWAPIndicator vwapIndicator) {
        super(IndicatorUtils.requireSameSeries(priceIndicator, vwapIndicator));
        this.priceIndicator = priceIndicator;
        this.difference = BinaryOperationIndicator.difference(priceIndicator, vwapIndicator);
        this.vwapIndicator = vwapIndicator;
    }

    /**
     * Calculates the indicator value at the requested index.
     */
    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        if (index < beginIndex || index < beginIndex + getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        Num value = difference.getValue(index);
        return Num.isNaNOrNull(value) ? NaN.NaN : value;
    }

    /**
     * Returns the number of unstable bars required before values become reliable.
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(priceIndicator.getCountOfUnstableBars(), vwapIndicator.getCountOfUnstableBars());
    }
}
