/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.adx;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * -DM indicator.
 *
 * <p>
 * Part of the Directional Movement System.
 */
public class MinusDMIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public MinusDMIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return getBarSeries().numFactory().zero();
        }
        final Bar prevBar = getBarSeries().getBar(index - 1);
        final Bar currentBar = getBarSeries().getBar(index);

        final Num upMove = currentBar.getHighPrice().minus(prevBar.getHighPrice());
        final Num downMove = prevBar.getLowPrice().minus(currentBar.getLowPrice());
        if (downMove.isGreaterThan(upMove) && downMove.isGreaterThan(getBarSeries().numFactory().zero())) {
            return downMove;
        } else {
            return getBarSeries().numFactory().zero();
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
