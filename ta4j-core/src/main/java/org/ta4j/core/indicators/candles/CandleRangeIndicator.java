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
 * Candle range indicator.
 *
 * <p>
 * Provides the full range of the current candle, i.e. {@code high - low}. The
 * value is the total vertical span of the candle and equals the sum of the
 * upper shadow, the body, and the lower shadow for well-formed OHLC bars. Bars
 * whose high or low price is missing or non-finite report {@link NaN#NaN}. A
 * range whose finite subtraction overflows (for example
 * {@code Double.MAX_VALUE} minus {@code -Double.MAX_VALUE}) is preserved as an
 * infinite magnitude rather than clamped: its operands are finite, so strict
 * comparisons against thresholds built from it stay decidable.
 * <p>
 * This is <b>not</b> the true range: the true range also considers the previous
 * close (see the ATR indicator for that quantity). This indicator depends only
 * on the current candle.
 *
 * @since 0.24.2
 */
public class CandleRangeIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public CandleRangeIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Bar t = getBarSeries().getBar(index);
        Num high = t.getHighPrice();
        Num low = t.getLowPrice();
        if (!Num.isFinite(high) || !Num.isFinite(low)) {
            return NaN.NaN;
        }
        return high.minus(low);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
