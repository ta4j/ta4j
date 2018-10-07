package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * High price indicator.
 * </p>
 */
public class HighPriceIndicator extends CachedIndicator<Num> {

    public HighPriceIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        return getTimeSeries().getBar(index).getHighPrice();
    }
}
