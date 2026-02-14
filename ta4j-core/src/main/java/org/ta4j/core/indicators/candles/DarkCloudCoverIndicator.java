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
 * Dark cloud cover candlestick pattern indicator.
 *
 * <p>
 * A dark cloud cover pattern is detected when a long bullish candle in an
 * uptrend is followed by a long bearish candle that gaps up at the open and
 * closes deep into the first candle's body.
 * </p>
 *
 * <p>
 * Default thresholds:
 * </p>
 * <ul>
 * <li>{@code bigBodyThresholdPercentage = 0.03} (3%)</li>
 * <li>{@code gapThresholdPercentage = 0.0} (any strict gap up)</li>
 * <li>{@code penetrationThresholdPercentage = 0.5} (close below midpoint)</li>
 * </ul>
 *
 * @see <a href="https://www.investopedia.com/terms/d/darkcloudcover.asp">Dark
 *      Cloud Cover</a>
 * @since 0.22.2
 */
public class DarkCloudCoverIndicator extends CachedIndicator<Boolean> {

    private final UpTrendIndicator trendIndicator;
    private final RealBodyIndicator realBodyIndicator;
    private final Num bigBodyThresholdPercentage;
    private final Num gapThresholdPercentage;
    private final Num penetrationThresholdPercentage;

    /**
     * Constructor using default thresholds.
     *
     * @param series the bar series
     */
    public DarkCloudCoverIndicator(final BarSeries series) {
        this(series, series.numFactory().numOf(0.03), series.numFactory().zero(), series.numFactory().numOf(0.5));
    }

    /**
     * Constructor exposing all tunable thresholds.
     *
     * @param series                         the bar series
     * @param bigBodyThresholdPercentage     minimum body size ratio (body/open)
     * @param gapThresholdPercentage         minimum gap up ratio from first close
     *                                       to second open
     * @param penetrationThresholdPercentage minimum penetration ratio into the
     *                                       first body measured from first close
     *                                       downward (uses strict {@code <}
     *                                       comparison)
     */
    public DarkCloudCoverIndicator(final BarSeries series, final Num bigBodyThresholdPercentage,
            final Num gapThresholdPercentage, final Num penetrationThresholdPercentage) {
        super(series);
        this.trendIndicator = new UpTrendIndicator(series);
        this.realBodyIndicator = new RealBodyIndicator(series);
        this.bigBodyThresholdPercentage = bigBodyThresholdPercentage;
        this.gapThresholdPercentage = gapThresholdPercentage;
        this.penetrationThresholdPercentage = penetrationThresholdPercentage;
    }

    @Override
    protected Boolean calculate(final int index) {
        if (index < getCountOfUnstableBars()) {
            return false;
        }

        final Bar firstBar = getBarSeries().getBar(index - 1);
        final Bar secondBar = getBarSeries().getBar(index);
        final Num firstBodyRatio = realBodyIndicator.getValue(index - 1).abs().dividedBy(firstBar.getOpenPrice());
        final Num secondBodyRatio = realBodyIndicator.getValue(index).abs().dividedBy(secondBar.getOpenPrice());

        final Num firstBodySize = firstBar.getClosePrice().minus(firstBar.getOpenPrice());
        final Num requiredClose = firstBar.getClosePrice()
                .minus(firstBodySize.multipliedBy(penetrationThresholdPercentage));
        final Num gapRatio = secondBar.getOpenPrice()
                .minus(firstBar.getClosePrice())
                .dividedBy(firstBar.getClosePrice());

        return firstBar.isBullish() && firstBodyRatio.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && secondBar.isBearish() && secondBodyRatio.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && secondBar.getOpenPrice().isGreaterThan(firstBar.getClosePrice())
                && gapRatio.isGreaterThanOrEqual(gapThresholdPercentage)
                && secondBar.getClosePrice().isLessThan(requiredClose)
                && secondBar.getClosePrice().isGreaterThan(firstBar.getOpenPrice()) && trendIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(1, trendIndicator.getCountOfUnstableBars());
    }
}
