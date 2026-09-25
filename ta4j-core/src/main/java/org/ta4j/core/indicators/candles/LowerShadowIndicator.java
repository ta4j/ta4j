/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Lower shadow height indicator.
 *
 * <p>
 * Provides the (absolute) difference between the lowest price of the candle
 * body and the low price of the candle. I.e.: min(open price, close price) -
 * low price. The value is the length of the lower wick and is always
 * non-negative for well-formed OHLC bars.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation">
 *      http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation</a>
 */
public class LowerShadowIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public LowerShadowIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Bar t = getBarSeries().getBar(index);
        final Num openPrice = t.getOpenPrice();
        final Num closePrice = t.getClosePrice();
        if (closePrice.isGreaterThan(openPrice)) {
            // Bullish
            return openPrice.minus(t.getLowPrice());
        } else {
            // Bearish
            return closePrice.minus(t.getLowPrice());
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
