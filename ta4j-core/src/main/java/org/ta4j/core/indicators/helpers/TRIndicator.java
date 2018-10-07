package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * True range indicator.
 * <p/>
 */
public class TRIndicator extends CachedIndicator<Num> {

    public TRIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Num ts = getTimeSeries().getBar(index).getHighPrice().minus(getTimeSeries().getBar(index).getLowPrice());
        Num ys = index == 0 ? numOf(0) : getTimeSeries().getBar(index).getHighPrice().minus(getTimeSeries().getBar(index - 1).getClosePrice());
        Num yst = index == 0 ? numOf(0) : getTimeSeries().getBar(index - 1).getClosePrice().minus(getTimeSeries().getBar(index).getLowPrice());
        return ts.abs().max(ys.abs()).max(yst.abs());
    }
}
