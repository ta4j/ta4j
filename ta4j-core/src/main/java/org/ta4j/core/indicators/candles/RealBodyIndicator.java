package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Real (candle) body height indicator.
 * </p>
 * Provides the (relative) difference between the open price and the close price of a bar.
 * I.e.: close price - open price
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation">
 *     http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation</a>
 */
public class RealBodyIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     * @param series a time series
     */
    public RealBodyIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Bar t = getTimeSeries().getBar(index);
        return t.getClosePrice().minus(t.getOpenPrice());
    }
}
