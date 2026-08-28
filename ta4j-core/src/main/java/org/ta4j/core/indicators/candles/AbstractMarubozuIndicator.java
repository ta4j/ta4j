/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Shared logic for Marubozu-style candlestick pattern indicators.
 *
 * <p>
 * A Marubozu candle is characterised by a long real body with very small upper
 * and lower shadows. Concrete subclasses decide whether the body must be
 * bullish (close &gt; open) or bearish (open &gt; close). A candle with a zero
 * body (open equals close) satisfies neither direction.
 *
 * @since 0.19
 */
abstract class AbstractMarubozuIndicator extends CachedIndicator<Boolean> {

    static final int DEFAULT_BODY_AVERAGE_PERIOD = 5;
    static final double DEFAULT_BODY_TO_AVERAGE_BODY_RATIO = 1d;
    static final double DEFAULT_UPPER_SHADOW_TO_BODY_RATIO = 0.05d;
    static final double DEFAULT_LOWER_SHADOW_TO_BODY_RATIO = 0.05d;

    private final transient CandleBodyIndicator bodyHeightIndicator;
    private final transient SMAIndicator averageBodyHeightIndicator;
    private final transient UpperShadowIndicator upperShadowIndicator;
    private final transient LowerShadowIndicator lowerShadowIndicator;
    private final int bodyAveragePeriod;
    private final double bodyToAverageBodyRatio;
    private final double upperShadowToBodyRatio;
    private final double lowerShadowToBodyRatio;
    private final transient Num bodyToAverageBodyRatioThreshold;
    private final transient Num upperShadowToBodyRatioThreshold;
    private final transient Num lowerShadowToBodyRatioThreshold;

    AbstractMarubozuIndicator(final BarSeries series) {
        this(validatedConfig(series, DEFAULT_BODY_AVERAGE_PERIOD, DEFAULT_BODY_TO_AVERAGE_BODY_RATIO,
                DEFAULT_UPPER_SHADOW_TO_BODY_RATIO, DEFAULT_LOWER_SHADOW_TO_BODY_RATIO));
    }

    AbstractMarubozuIndicator(final BarSeries series, final int bodyAveragePeriod, final double bodyToAverageBodyRatio,
            final double upperShadowToBodyRatio, final double lowerShadowToBodyRatio) {
        this(validatedConfig(series, bodyAveragePeriod, bodyToAverageBodyRatio, upperShadowToBodyRatio,
                lowerShadowToBodyRatio));
    }

    private AbstractMarubozuIndicator(final Config config) {
        super(config.series());
        this.bodyAveragePeriod = config.bodyAveragePeriod();
        this.bodyToAverageBodyRatio = config.bodyToAverageBodyRatio();
        this.upperShadowToBodyRatio = config.upperShadowToBodyRatio();
        this.lowerShadowToBodyRatio = config.lowerShadowToBodyRatio();
        this.bodyHeightIndicator = config.bodyHeightIndicator();
        this.averageBodyHeightIndicator = config.averageBodyHeightIndicator();
        this.upperShadowIndicator = config.upperShadowIndicator();
        this.lowerShadowIndicator = config.lowerShadowIndicator();
        this.bodyToAverageBodyRatioThreshold = config.bodyToAverageBodyRatioThreshold();
        this.upperShadowToBodyRatioThreshold = config.upperShadowToBodyRatioThreshold();
        this.lowerShadowToBodyRatioThreshold = config.lowerShadowToBodyRatioThreshold();
    }

    private static Config validatedConfig(final BarSeries series, final int bodyAveragePeriod,
            final double bodyToAverageBodyRatio, final double upperShadowToBodyRatio,
            final double lowerShadowToBodyRatio) {
        BarSeries validatedSeries = Objects.requireNonNull(series, "series must not be null");
        if (bodyAveragePeriod < 1) {
            throw new IllegalArgumentException("bodyAveragePeriod must be >= 1");
        }
        if (bodyToAverageBodyRatio <= 0d) {
            throw new IllegalArgumentException("bodyToAverageBodyRatio must be > 0");
        }
        if (upperShadowToBodyRatio < 0d) {
            throw new IllegalArgumentException("upperShadowToBodyRatio must be >= 0");
        }
        if (lowerShadowToBodyRatio < 0d) {
            throw new IllegalArgumentException("lowerShadowToBodyRatio must be >= 0");
        }
        CandleBodyIndicator bodyHeightIndicator = new CandleBodyIndicator(validatedSeries);
        SMAIndicator averageBodyHeightIndicator = new SMAIndicator(bodyHeightIndicator, bodyAveragePeriod);
        UpperShadowIndicator upperShadowIndicator = new UpperShadowIndicator(validatedSeries);
        LowerShadowIndicator lowerShadowIndicator = new LowerShadowIndicator(validatedSeries);

        final NumFactory numFactory = validatedSeries.numFactory();
        Num bodyToAverageBodyRatioThreshold = numFactory.numOf(bodyToAverageBodyRatio);
        Num upperShadowToBodyRatioThreshold = numFactory.numOf(upperShadowToBodyRatio);
        Num lowerShadowToBodyRatioThreshold = numFactory.numOf(lowerShadowToBodyRatio);
        return new Config(validatedSeries, bodyHeightIndicator, averageBodyHeightIndicator, upperShadowIndicator,
                lowerShadowIndicator, bodyAveragePeriod, bodyToAverageBodyRatio, upperShadowToBodyRatio,
                lowerShadowToBodyRatio, bodyToAverageBodyRatioThreshold, upperShadowToBodyRatioThreshold,
                lowerShadowToBodyRatioThreshold);
    }

    @Override
    protected Boolean calculate(final int index) {
        if (index < this.bodyAveragePeriod) {
            return false;
        }

        if (!hasExpectedBodyDirection(index)) {
            return false;
        }

        final var bodyHeight = this.bodyHeightIndicator.getValue(index);
        final var averageBodyHeight = this.averageBodyHeightIndicator.getValue(index - 1);
        if (!bodyHeight.isGreaterThan(averageBodyHeight.multipliedBy(this.bodyToAverageBodyRatioThreshold))) {
            return false;
        }

        return hasSmallShadows(index, bodyHeight);
    }

    @Override
    public int getCountOfUnstableBars() {
        return this.bodyAveragePeriod;
    }

    private boolean hasSmallShadows(final int index, final Num bodyHeight) {
        final var upperShadow = this.upperShadowIndicator.getValue(index);
        final var lowerShadow = this.lowerShadowIndicator.getValue(index);
        final var maxUpperShadow = bodyHeight.multipliedBy(this.upperShadowToBodyRatioThreshold);
        final var maxLowerShadow = bodyHeight.multipliedBy(this.lowerShadowToBodyRatioThreshold);
        return upperShadow.isLessThanOrEqual(maxUpperShadow) && lowerShadow.isLessThanOrEqual(maxLowerShadow);
    }

    private boolean hasExpectedBodyDirection(final int index) {
        final Bar bar = getBarSeries().getBar(index);
        return isBullish() ? bar.isBullish() : bar.isBearish();
    }

    /**
     * @return {@code true} if the Marubozu requires a bullish candle, {@code false}
     *         if it requires a bearish candle.
     * @since 0.19
     */
    protected abstract boolean isBullish();

    private record Config(BarSeries series, CandleBodyIndicator bodyHeightIndicator,
            SMAIndicator averageBodyHeightIndicator, UpperShadowIndicator upperShadowIndicator,
            LowerShadowIndicator lowerShadowIndicator, int bodyAveragePeriod, double bodyToAverageBodyRatio,
            double upperShadowToBodyRatio, double lowerShadowToBodyRatio, Num bodyToAverageBodyRatioThreshold,
            Num upperShadowToBodyRatioThreshold, Num lowerShadowToBodyRatioThreshold) {
    }
}
