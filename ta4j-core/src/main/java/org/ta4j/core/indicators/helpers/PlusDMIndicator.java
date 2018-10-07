package org.ta4j.core.indicators.helpers;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
/**
 * +DM indicator.
 * <p/>
 */
public class PlusDMIndicator extends CachedIndicator<Num> {

    public PlusDMIndicator(TimeSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return numOf(0);
        }
        Num upMove = getTimeSeries().getBar(index).getHighPrice().minus(getTimeSeries().getBar(index - 1).getHighPrice());
        Num downMove = getTimeSeries().getBar(index - 1).getLowPrice().minus(getTimeSeries().getBar(index).getLowPrice());
        if (upMove.isGreaterThan(downMove) && upMove.isGreaterThan(numOf(0))) {
            return upMove;
        } else {
            return numOf(0);
        }
    }
}
