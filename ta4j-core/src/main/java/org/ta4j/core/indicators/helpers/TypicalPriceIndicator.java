package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Typical price indicator.
 * </p>
 */
public class TypicalPriceIndicator extends CachedIndicator<Num> {

    public TypicalPriceIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Num maxPrice = getTimeSeries().getBar(index).getHighPrice();
        Num minPrice = getTimeSeries().getBar(index).getLowPrice();
        Num closePrice = getTimeSeries().getBar(index).getClosePrice();
        return maxPrice.plus(minPrice).plus(closePrice).dividedBy(numOf(3));
    }
}