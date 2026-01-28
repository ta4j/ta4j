/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.trend.DownTrendIndicator;

/**
 * Three inside up candle indicator.
 *
 * @see <a href="https://www.investopedia.com/terms/t/three-inside-updown.asp">
 *      https://www.investopedia.com/terms/t/three-inside-updown.asp</a>
 * @since 0.22.2
 */
public class ThreeInsideUpIndicator extends CachedIndicator<Boolean> {

    private final DownTrendIndicator trendIndicator;
    private final BullishHaramiIndicator harami;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public ThreeInsideUpIndicator(final BarSeries series) {
        super(series);
        this.trendIndicator = new DownTrendIndicator(series);
        this.harami = new BullishHaramiIndicator(series);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return false;
        }
        Bar firstBar = getBarSeries().getBar(index - 2);
        Bar thirdBar = getBarSeries().getBar(index);

        return harami.getValue(index - 1) && thirdBar.getClosePrice().isGreaterThan(firstBar.getOpenPrice())
                && thirdBar.isBullish() && this.trendIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 2;
    }
}
