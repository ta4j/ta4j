/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.trend.DownTrendIndicator;
import org.ta4j.core.num.Num;

/**
 * Bullish kicker candle indicator.
 *
 * @see <a href="https://www.investopedia.com/terms/k/kickerpattern.asp">
 *      https://www.investopedia.com/terms/k/kickerpattern.asp</a>
 * @since 0.22.2
 */
public class BullishKickerIndicator extends CachedIndicator<Boolean> {

    private final DownTrendIndicator trendIndicator;
    private final RealBodyIndicator realBodyIndicator;
    private final Num bigBodyThresholdPercentage;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public BullishKickerIndicator(final BarSeries series) {
        this(series, series.numFactory().numOf(0.03));
    }

    /**
     * Constructor.
     *
     * @param series                     the bar series
     * @param bigBodyThresholdPercentage percentage to determine whether a candle
     *                                   has a big body or not
     */
    public BullishKickerIndicator(final BarSeries series, final Num bigBodyThresholdPercentage) {
        super(series);
        this.trendIndicator = new DownTrendIndicator(series);
        this.realBodyIndicator = new RealBodyIndicator(series);
        this.bigBodyThresholdPercentage = bigBodyThresholdPercentage;
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return false;
        }
        Bar firstBar = getBarSeries().getBar(index - 1);
        Bar secondBar = getBarSeries().getBar(index);
        Num firstBarPercentage = this.realBodyIndicator.getValue(index - 1).abs().dividedBy(firstBar.getOpenPrice());
        Num secondBarPercentage = this.realBodyIndicator.getValue(index).abs().dividedBy(secondBar.getOpenPrice());

        return firstBar.isBearish() && firstBarPercentage.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && secondBar.isBullish() && secondBarPercentage.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && secondBar.getOpenPrice().isGreaterThan(firstBar.getClosePrice())
                && secondBar.getClosePrice().isGreaterThan(firstBar.getOpenPrice())
                && this.trendIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 1;
    }
}
