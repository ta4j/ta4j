/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * True range indicator.
 *
 * <pre>
 * TrueRange = MAX(high - low, high - previousClose, previousClose - low)
 * </pre>
 */
public class TRIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> highPriceIndicator;
    private final Indicator<Num> lowPriceIndicator;
    private final Indicator<Num> closePriceIndicator;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public TRIndicator(BarSeries series) {
        this(new HighPriceIndicator(series), new LowPriceIndicator(series), new ClosePriceIndicator(series));
    }

    /**
     * Constructor.
     *
     * @param highPriceIndicator  high-price indicator
     * @param lowPriceIndicator   low-price indicator
     * @param closePriceIndicator close-price indicator
     * @since 0.22.3
     */
    public TRIndicator(Indicator<Num> highPriceIndicator, Indicator<Num> lowPriceIndicator,
            Indicator<Num> closePriceIndicator) {
        super(IndicatorUtils.requireSameSeries(highPriceIndicator, lowPriceIndicator, closePriceIndicator));
        this.highPriceIndicator = highPriceIndicator;
        this.lowPriceIndicator = lowPriceIndicator;
        this.closePriceIndicator = closePriceIndicator;
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        Num high = highPriceIndicator.getValue(index);
        Num low = lowPriceIndicator.getValue(index);
        if (Num.isNaNOrNull(high) || Num.isNaNOrNull(low)) {
            return NaN;
        }
        Num hl = high.minus(low).abs();
        if (index <= getBarSeries().getBeginIndex()) {
            return hl;
        }

        Num previousClose = closePriceIndicator.getValue(index - 1);
        if (Num.isNaNOrNull(previousClose)) {
            return NaN;
        }
        Num hc = high.minus(previousClose).abs();
        Num cl = previousClose.minus(low).abs();
        return hl.max(hc).max(cl);

    }

    /**
     * Includes one additional bar only when the close input itself has a warm-up
     * window, because true range reads {@code close(index - 1)}.
     */
    @Override
    public int getCountOfUnstableBars() {
        int highUnstable = highPriceIndicator.getCountOfUnstableBars();
        int lowUnstable = lowPriceIndicator.getCountOfUnstableBars();
        int closeUnstable = closePriceIndicator.getCountOfUnstableBars();
        int previousCloseUnstable = closeUnstable == 0 ? 0 : closeUnstable + 1;
        return Math.max(highUnstable, Math.max(lowUnstable, previousCloseUnstable));
    }
}
