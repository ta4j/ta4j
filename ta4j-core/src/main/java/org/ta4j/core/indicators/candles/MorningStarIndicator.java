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
 * Morning star candle indicator.
 *
 * @see <a href="https://www.investopedia.com/terms/m/morningstar.asp">
 *      https://www.investopedia.com/terms/m/morningstar.asp</a>
 * @since 0.22.2
 */
public class MorningStarIndicator extends CachedIndicator<Boolean> {

    private final DownTrendIndicator trendIndicator;
    private final RealBodyIndicator realBodyIndicator;
    private final Num smallBodyThresholdPercentage;
    private final Num bigBodyThresholdPercentage;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public MorningStarIndicator(final BarSeries series) {
        this(series, series.numFactory().numOf(0.015), series.numFactory().numOf(0.03));
    }

    /**
     * Constructor.
     *
     * @param series                       the bar series
     * @param smallBodyThresholdPercentage percentage to determine whether a candle
     *                                     has a small body or not
     * @param bigBodyThresholdPercentage   percentage to determine whether a candle
     *                                     has a big body or not
     */
    public MorningStarIndicator(final BarSeries series, final Num smallBodyThresholdPercentage,
            final Num bigBodyThresholdPercentage) {
        super(series);
        this.trendIndicator = new DownTrendIndicator(series);
        this.realBodyIndicator = new RealBodyIndicator(series);
        this.smallBodyThresholdPercentage = smallBodyThresholdPercentage;
        this.bigBodyThresholdPercentage = bigBodyThresholdPercentage;
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return false;
        }
        Bar firstBar = getBarSeries().getBar(index - 2);
        Bar secondBar = getBarSeries().getBar(index - 1);
        Bar thirdBar = getBarSeries().getBar(index);
        Num firstBarPercentage = this.realBodyIndicator.getValue(index - 2).abs().dividedBy(firstBar.getOpenPrice());
        Num secondBarPercentage = this.realBodyIndicator.getValue(index - 1).abs().dividedBy(secondBar.getOpenPrice());
        Num thirdBarPercentage = this.realBodyIndicator.getValue(index).abs().dividedBy(thirdBar.getOpenPrice());
        Num firstBarMiddlePoint = firstBar.getOpenPrice()
                .minus(firstBar.getClosePrice())
                .dividedBy(getBarSeries().numFactory().numOf(2))
                .plus(firstBar.getClosePrice());

        return firstBar.isBearish() && firstBarPercentage.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && secondBar.getOpenPrice().isLessThan(firstBar.getClosePrice())
                && secondBarPercentage.isLessThanOrEqual(smallBodyThresholdPercentage)
                && thirdBar.getClosePrice().isGreaterThan(firstBarMiddlePoint) && thirdBar.isBullish()
                && thirdBarPercentage.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && this.trendIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 2;
    }
}
