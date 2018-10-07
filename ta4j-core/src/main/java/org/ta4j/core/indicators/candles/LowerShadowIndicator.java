package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Lower shadow height indicator.
 * </p>
 * Provides the (absolute) difference between the min price and the lowest price of the candle body.
 * I.e.: min price - min(open price, close price)
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation">
 *     http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation</a>
 */
public class LowerShadowIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     * @param series a time series
     */
    public LowerShadowIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Bar t = getTimeSeries().getBar(index);
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
}
