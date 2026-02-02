/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.trend.UpTrendIndicator;
import org.ta4j.core.num.Num;

/**
 * Dark cloud candle indicator.
 *
 * @see <a href="https://www.investopedia.com/terms/d/darkcloud.asp">
 *      https://www.investopedia.com/terms/d/darkcloud.asp</a>
 * @since 0.22.2
 */
public class DarkCloudIndicator extends CachedIndicator<Boolean> {

    private final UpTrendIndicator trendIndicator;
    private final RealBodyIndicator realBodyIndicator;
    private final Num bigBodyThresholdPercentage;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public DarkCloudIndicator(final BarSeries series) {
        this(series, series.numFactory().numOf(0.03));
    }

    /**
     * Constructor.
     *
     * @param series                     the bar series
     * @param bigBodyThresholdPercentage percentage to determine whether a candle
     *                                   has a big body or not
     */
    public DarkCloudIndicator(final BarSeries series, final Num bigBodyThresholdPercentage) {
        super(series);
        this.trendIndicator = new UpTrendIndicator(series);
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
        Num firstBarMiddlePoint = firstBar.getClosePrice()
                .minus(firstBar.getOpenPrice())
                .dividedBy(getBarSeries().numFactory().numOf(2))
                .plus(firstBar.getOpenPrice());

        return firstBar.isBullish() && firstBarPercentage.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && secondBar.isBearish() && secondBarPercentage.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && secondBar.getOpenPrice().isGreaterThan(firstBar.getClosePrice())
                && secondBar.getClosePrice().isLessThan(firstBarMiddlePoint) && this.trendIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 1;
    }
}
