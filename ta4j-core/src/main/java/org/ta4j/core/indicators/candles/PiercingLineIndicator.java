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
 * Piercing line candlestick pattern indicator.
 *
 * <p>
 * A piercing line pattern is detected when a long bearish candle in a downtrend
 * is followed by a long bullish candle that gaps down at the open and closes
 * deep into the first candle's body.
 * </p>
 *
 * <p>
 * Default thresholds:
 * </p>
 * <ul>
 * <li>{@code bigBodyThresholdPercentage = 0.03} (3%)</li>
 * <li>{@code gapThresholdPercentage = 0.0} (any strict gap down)</li>
 * <li>{@code penetrationThresholdPercentage = 0.5} (close above midpoint)</li>
 * </ul>
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/p/piercingpattern.asp">Piercing
 *      Pattern</a>
 * @since 0.22.2
 */
public class PiercingLineIndicator extends CachedIndicator<Boolean> {

    private final DownTrendIndicator trendIndicator;
    private final RealBodyIndicator realBodyIndicator;
    private final Num bigBodyThresholdPercentage;
    private final Num gapThresholdPercentage;
    private final Num penetrationThresholdPercentage;

    /**
     * Constructor using default thresholds.
     *
     * @param series the bar series
     */
    public PiercingLineIndicator(final BarSeries series) {
        this(series, series.numFactory().numOf(0.03), series.numFactory().zero(), series.numFactory().numOf(0.5));
    }

    /**
     * Constructor exposing all tunable thresholds.
     *
     * @param series                         the bar series
     * @param bigBodyThresholdPercentage     minimum body size ratio (body/open)
     * @param gapThresholdPercentage         minimum gap down ratio from first close
     *                                       to second open
     * @param penetrationThresholdPercentage minimum penetration ratio into the
     *                                       first body measured from first close
     *                                       upward (uses strict {@code >}
     *                                       comparison)
     */
    public PiercingLineIndicator(final BarSeries series, final Num bigBodyThresholdPercentage,
            final Num gapThresholdPercentage, final Num penetrationThresholdPercentage) {
        super(series);
        this.trendIndicator = new DownTrendIndicator(series);
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

        final Num firstBodySize = firstBar.getOpenPrice().minus(firstBar.getClosePrice());
        final Num requiredClose = firstBar.getClosePrice()
                .plus(firstBodySize.multipliedBy(penetrationThresholdPercentage));
        final Num gapRatio = firstBar.getClosePrice()
                .minus(secondBar.getOpenPrice())
                .dividedBy(firstBar.getClosePrice());

        return firstBar.isBearish() && firstBodyRatio.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && secondBar.isBullish() && secondBodyRatio.isGreaterThanOrEqual(bigBodyThresholdPercentage)
                && secondBar.getOpenPrice().isLessThan(firstBar.getClosePrice())
                && gapRatio.isGreaterThanOrEqual(gapThresholdPercentage)
                && secondBar.getClosePrice().isGreaterThan(requiredClose)
                && secondBar.getClosePrice().isLessThan(firstBar.getOpenPrice()) && trendIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(1, trendIndicator.getCountOfUnstableBars());
    }
}
