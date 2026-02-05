/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * True range indicator.
 *
 * <pre>
 * TrueRange = MAX(high - low, high - previousClose, previousClose - low)
 * </pre>
 */
public class TRIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public TRIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Bar bar = getBarSeries().getBar(index);
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();
        if (Num.isNaNOrNull(high) || Num.isNaNOrNull(low)) {
            return NaN;
        }
        Num hl = high.minus(low);

        if (index == 0) {
            return hl.abs();
        }

        Num previousClose = getBarSeries().getBar(index - 1).getClosePrice();
        if (Num.isNaNOrNull(previousClose)) {
            return NaN;
        }
        Num hc = high.minus(previousClose);
        Num cl = previousClose.minus(low);
        return hl.abs().max(hc.abs()).max(cl.abs());

    }

    /** @return {@code 0} */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
