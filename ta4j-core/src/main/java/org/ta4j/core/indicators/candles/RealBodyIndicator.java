/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Real (candle) body height indicator.
 *
 * <p>
 * Provides the (relative) difference between the open price and the close price
 * of a bar. I.e.: close price - open price. The value is negative for bearish
 * candles and positive for bullish candles.
 *
 * <p>
 * This legacy signed quantity is easy to misuse when only the body magnitude
 * matters. Prefer {@link CandleBodyIndicator} for magnitude-based candle
 * algebra and {@link Bar#isBullish()} / {@link Bar#isBearish()} for direction.
 * This indicator is retained for backward compatibility and for consumers that
 * need the signed close-to-open change.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation">
 *      http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation</a>
 * @deprecated Use {@link CandleBodyIndicator} for body magnitude or
 *             {@link Bar#isBullish()}/{@link Bar#isBearish()} for direction.
 */
@Deprecated
public class RealBodyIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public RealBodyIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Bar t = getBarSeries().getBar(index);
        return t.getClosePrice().minus(t.getOpenPrice());
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
