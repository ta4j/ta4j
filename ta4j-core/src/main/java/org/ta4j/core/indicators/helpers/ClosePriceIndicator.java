package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Close price indicator.
 * </p>
 */
public class ClosePriceIndicator extends CachedIndicator<Num> {

    public ClosePriceIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        return getTimeSeries().getBar(index).getClosePrice();
    }
}
