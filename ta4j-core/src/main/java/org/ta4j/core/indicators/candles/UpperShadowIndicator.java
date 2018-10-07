package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Upper shadow height indicator.
 * </p>
 * Provides the (absolute) difference between the max price and the highest price of the candle body.
 * I.e.: max price - max(open price, close price)
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation">
 *     http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation</a>
 */
public class UpperShadowIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     * @param series a time series
     */
    public UpperShadowIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Bar t = getTimeSeries().getBar(index);
        final Num openPrice = t.getOpenPrice();
        final Num closePrice = t.getClosePrice();
        if (closePrice.isGreaterThan(openPrice)) {
            // Bullish
            return t.getHighPrice().minus(closePrice);
        } else {
            // Bearish
            return t.getHighPrice().minus(openPrice);
        }
    }
}
