/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.trend.DownTrendIndicator;

/**
 * Hammer candle indicator.
 *
 * @see <a href="https://www.investopedia.com/terms/h/hammer.asp">
 *      https://www.investopedia.com/terms/h/hammer.asp</a>
 */
public class HammerIndicator extends CachedIndicator<Boolean> {

    private static final double DEFAULT_BODY_LENGTH_TO_BOTTOM_WICK_COEFFICIENT = 2d;
    private static final double DEFAULT_BODY_LENGTH_TO_UPPER_WICK_COEFFICIENT = 1d;

    private final RealBodyIndicator realBodyIndicator;
    private final DownTrendIndicator trendIndicator;
    private final double bodyToBottomWickRatio;
    private final double bodyToUpperWickRatio;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public HammerIndicator(final BarSeries series) {
        super(series);
        this.realBodyIndicator = new RealBodyIndicator(series);
        this.trendIndicator = new DownTrendIndicator(series);
        this.bodyToBottomWickRatio = DEFAULT_BODY_LENGTH_TO_BOTTOM_WICK_COEFFICIENT;
        this.bodyToUpperWickRatio = DEFAULT_BODY_LENGTH_TO_UPPER_WICK_COEFFICIENT;
    }

    /**
     * Constructor.
     *
     * @param series                the bar series
     * @param bodyToBottomWickRatio the body to bottom wick ratio
     * @param bodyToUpperWickRatio  the body to upper wick ratio
     */
    public HammerIndicator(final BarSeries series, double bodyToBottomWickRatio, double bodyToUpperWickRatio) {
        super(series);
        this.realBodyIndicator = new RealBodyIndicator(series);
        this.trendIndicator = new DownTrendIndicator(series);
        this.bodyToBottomWickRatio = bodyToBottomWickRatio;
        this.bodyToUpperWickRatio = bodyToUpperWickRatio;
    }

    @Override
    protected Boolean calculate(final int index) {
        final var bar = getBarSeries().getBar(index);
        final var openPrice = bar.getOpenPrice();
        final var closePrice = bar.getClosePrice();
        final var lowPrice = bar.getLowPrice();
        final var highPrice = bar.getHighPrice();

        final var bodyHeight = this.realBodyIndicator.getValue(index).abs();

        final var upperBodyBoundary = openPrice.max(closePrice);
        final var bottomBodyBoundary = openPrice.min(closePrice);
        final var bottomWickHeight = bottomBodyBoundary.minus(lowPrice);
        final var upperWickHeight = highPrice.minus(upperBodyBoundary);

        return bottomWickHeight.dividedBy(bodyHeight)
                .isGreaterThan(getBarSeries().numFactory().numOf(this.bodyToBottomWickRatio))
                && upperWickHeight.dividedBy(bodyHeight)
                        .isLessThanOrEqual(getBarSeries().numFactory().numOf(this.bodyToUpperWickRatio))
                && this.trendIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
