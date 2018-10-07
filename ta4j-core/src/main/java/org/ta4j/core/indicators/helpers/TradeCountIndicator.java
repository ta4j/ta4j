package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * Trade count indicator.
 * </p>
 */
public class TradeCountIndicator extends CachedIndicator<Integer> {

    private static final long serialVersionUID = -925772914642803594L;

    public TradeCountIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Integer calculate(int index) {
        return getTimeSeries().getBar(index).getTrades();
    }
}