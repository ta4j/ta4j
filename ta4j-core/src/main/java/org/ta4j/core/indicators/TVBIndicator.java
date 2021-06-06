package org.ta4j.core.indicators;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * TVB (True Value of Bar) indicator
 */
public class TVBIndicator extends AbstractIndicator<Num> {

    protected TVBIndicator(BarSeries series) {
        super(series);
    }

    @Override
    public Num getValue(int index) {
        final Bar bar = getBarSeries().getBar(index);

        return bar.getClosePrice().multipliedBy(numOf(3)).minus(bar.getLowPrice().plus(bar.getOpenPrice().plus(bar.getHighPrice())));
    }
}
