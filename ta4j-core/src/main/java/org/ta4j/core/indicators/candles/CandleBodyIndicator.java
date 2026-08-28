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
 * close price is missing or non-finite, or whose subtraction overflows, report
 * {@link NaN#NaN}.
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
        Num body = close.minus(open).abs();
        return Num.isFinite(body) ? body : NaN.NaN;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
