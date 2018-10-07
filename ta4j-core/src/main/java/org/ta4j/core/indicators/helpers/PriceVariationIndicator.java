package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Price variation indicator.
 * </p>
 */
public class PriceVariationIndicator extends CachedIndicator<Num> {

    public PriceVariationIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Num previousBarClosePrice = getTimeSeries().getBar(Math.max(0, index - 1)).getClosePrice();
        Num currentBarClosePrice = getTimeSeries().getBar(index).getClosePrice();
        return currentBarClosePrice.dividedBy(previousBarClosePrice);
    }
}