package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Bearish engulfing pattern indicator.
 * </p>
 * @see <a href="http://www.investopedia.com/terms/b/bearishengulfingp.asp">
 *     http://www.investopedia.com/terms/b/bearishengulfingp.asp</a>
 */
public class BearishEngulfingIndicator extends CachedIndicator<Boolean> {

    /**
     * Constructor.
     * @param series a time series
     */
    public BearishEngulfingIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < 1) {
            // Engulfing is a 2-candle pattern
            return false;
        }
        Bar prevBar = getTimeSeries().getBar(index - 1);
        Bar currBar = getTimeSeries().getBar(index);
        if (prevBar.isBullish() && currBar.isBearish()) {
            final Num prevOpenPrice = prevBar.getOpenPrice();
            final Num prevClosePrice = prevBar.getClosePrice();
            final Num currOpenPrice = currBar.getOpenPrice();
            final Num currClosePrice = currBar.getClosePrice();
            return currOpenPrice.isGreaterThan(prevOpenPrice) && currOpenPrice.isGreaterThan(prevClosePrice)
                    && currClosePrice.isLessThan(prevOpenPrice) && currClosePrice.isLessThan(prevClosePrice);
        }
        return false;
    }
}
