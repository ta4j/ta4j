/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Candle body height indicator.
 *
 * <p>
 * Provides the absolute difference between the open price and the close price
 * of a bar, i.e. {@code |close - open|}. The value is the magnitude of the real
 * body and is always non-negative for well-formed OHLC bars. Bars whose open or
 * close price is missing or non-finite report {@link NaN#NaN}; a subtraction
 * that overflows finite operands reports the non-finite overflow magnitude
 * instead, keeping an overflowed body distinguishable from an unavailable one.
 *
 * <p>
 * Unlike the deprecated {@link RealBodyIndicator}, which reports the signed
 * close-to-open change, this indicator reports the body magnitude. Directional
 * checks should use {@link Bar#isBullish()} or {@link Bar#isBearish()} instead
 * of the sign of the real body.
 *
 * @since 0.24.2
 */
public class CandleBodyIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public CandleBodyIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Bar t = getBarSeries().getBar(index);
        Num open = t.getOpenPrice();
        Num close = t.getClosePrice();
        if (!Num.isFinite(open) || !Num.isFinite(close)) {
            return NaN.NaN;
        }
        // A body whose finite operands overflow the numeric type (|close -
        // open| beyond the representable range) stays a non-finite magnitude
        // instead of being clamped to NaN, so consumers can distinguish an
        // overflowed body from a body missing because its inputs are genuinely
        // unavailable.
        return close.minus(open).abs();
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
