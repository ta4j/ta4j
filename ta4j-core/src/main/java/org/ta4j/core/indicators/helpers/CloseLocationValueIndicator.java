package org.ta4j.core.indicators.helpers;


import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Close Location Value (CLV) indicator.
 * </p>
 * @see <a href="http://www.investopedia.com/terms/c/close_location_value.asp">
 *     http://www.investopedia.com/terms/c/close_location_value.asp</a>
 */
public class CloseLocationValueIndicator extends CachedIndicator<Num> {

    public CloseLocationValueIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Bar bar = getTimeSeries().getBar(index);

        return ((bar.getClosePrice().minus(bar.getLowPrice())).minus(bar.getHighPrice().minus(bar.getClosePrice())))
                 .dividedBy(bar.getHighPrice().minus(bar.getLowPrice()));
    }
}
