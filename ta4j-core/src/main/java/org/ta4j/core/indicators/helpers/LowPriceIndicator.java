package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Low price indicator.
 * </p>
 */
public class LowPriceIndicator extends CachedIndicator<Num> {

    public LowPriceIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        return getTimeSeries().getBar(index).getLowPrice();
    }
}
