package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Average high-low indicator.
 * </p>
 */
public class MedianPriceIndicator extends CachedIndicator<Num> {

    public MedianPriceIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        return getTimeSeries().getBar(index).getHighPrice().plus(getTimeSeries().getBar(index).getLowPrice())
                .dividedBy(numOf(2));
    }
}
