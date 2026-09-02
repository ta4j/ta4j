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

    private final transient DownTrendIndicator trendIndicator;
    private final transient BullishHaramiIndicator harami;

    /**
     * Constructor with the default average period.
     *
     * @param series the bar series
     */
    public ThreeInsideUpIndicator(final BarSeries series) {
        this(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD);
    }

    /**
     * Constructor with a custom average period for the harami body baselines.
     *
     * @param series        the bar series
     * @param averagePeriod the number of preceding candles averaged into the harami
     *                      body baselines (at least 1)
     * @since 0.24.2
     */
    public ThreeInsideUpIndicator(final BarSeries series, final int averagePeriod) {
        super(series);
        this.trendIndicator = new DownTrendIndicator(series);
        this.harami = new BullishHaramiIndicator(series, averagePeriod);
    }

    @Override
    public Boolean getValue(final int index) {
        // The harami evaluated at index - 1 needs its own baseline window
        // complete; gate pre-cache so a retained result cannot outlive it.
        final BarSeries series = getBarSeries();
        if (series != null && index >= series.getBeginIndex() && index <= series.getEndIndex()
                && !harami.thresholds.isValid(harami.latestBaselineIndex(index - 1))) {
            return false;
        }
        return super.getValue(index);
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
        return Math.max(2, trendIndicator.getCountOfUnstableBars());
    }
}
