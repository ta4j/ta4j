/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.numeric.UnaryOperationIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Shared logic for Marubozu-style candlestick pattern indicators.
 *
 * <p>
 * A Marubozu candle is characterised by a long real body with very small upper
 * and lower shadows. Concrete subclasses decide whether the body must be
 * bullish (close &gt; open) or bearish (open &gt; close).
 *
 * @since 0.19
 */
abstract class AbstractMarubozuIndicator extends CachedIndicator<Boolean> {

    static final int DEFAULT_BODY_AVERAGE_PERIOD = 5;
    static final double DEFAULT_BODY_TO_AVERAGE_BODY_RATIO = 1d;
    static final double DEFAULT_UPPER_SHADOW_TO_BODY_RATIO = 0.05d;
    static final double DEFAULT_LOWER_SHADOW_TO_BODY_RATIO = 0.05d;

    private final RealBodyIndicator realBodyIndicator;
    private final Indicator<Num> bodyHeightIndicator;
    private final SMAIndicator averageBodyHeightIndicator;
    private final UpperShadowIndicator upperShadowIndicator;
    private final LowerShadowIndicator lowerShadowIndicator;
    private final int bodyAveragePeriod;
    private final Num bodyToAverageBodyRatioThreshold;
    private final Num upperShadowToBodyRatioThreshold;
    private final Num lowerShadowToBodyRatioThreshold;
    private final Num zero;

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
        this.realBodyIndicator = config.realBodyIndicator();
        this.bodyHeightIndicator = config.bodyHeightIndicator();
        this.averageBodyHeightIndicator = config.averageBodyHeightIndicator();
        this.upperShadowIndicator = config.upperShadowIndicator();
        this.lowerShadowIndicator = config.lowerShadowIndicator();
        this.bodyToAverageBodyRatioThreshold = config.bodyToAverageBodyRatioThreshold();
        this.upperShadowToBodyRatioThreshold = config.upperShadowToBodyRatioThreshold();
        this.lowerShadowToBodyRatioThreshold = config.lowerShadowToBodyRatioThreshold();
        this.zero = config.zero();
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
        RealBodyIndicator realBodyIndicator = new RealBodyIndicator(validatedSeries);
        Indicator<Num> bodyHeightIndicator = UnaryOperationIndicator.abs(realBodyIndicator);
        SMAIndicator averageBodyHeightIndicator = new SMAIndicator(bodyHeightIndicator, bodyAveragePeriod);
        UpperShadowIndicator upperShadowIndicator = new UpperShadowIndicator(validatedSeries);
        LowerShadowIndicator lowerShadowIndicator = new LowerShadowIndicator(validatedSeries);

        final NumFactory numFactory = validatedSeries.numFactory();
        Num bodyToAverageBodyRatioThreshold = numFactory.numOf(bodyToAverageBodyRatio);
        Num upperShadowToBodyRatioThreshold = numFactory.numOf(upperShadowToBodyRatio);
        Num lowerShadowToBodyRatioThreshold = numFactory.numOf(lowerShadowToBodyRatio);
        Num zero = numFactory.zero();
        return new Config(validatedSeries, realBodyIndicator, bodyHeightIndicator, averageBodyHeightIndicator,
                upperShadowIndicator, lowerShadowIndicator, bodyAveragePeriod, bodyToAverageBodyRatioThreshold,
                upperShadowToBodyRatioThreshold, lowerShadowToBodyRatioThreshold, zero);
    }

    @Override
    protected Boolean calculate(final int index) {
        if (index < this.bodyAveragePeriod) {
            return false;
        }

        final var realBody = this.realBodyIndicator.getValue(index);
        if (!hasExpectedBodyDirection(realBody)) {
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

    private boolean hasExpectedBodyDirection(final Num realBody) {
        if (realBody.isZero()) {
            return false;
        }
        return isBullish() ? realBody.isGreaterThan(this.zero) : realBody.isLessThan(this.zero);
    }

    /**
     * @return {@code true} if the Marubozu requires a bullish candle, {@code false}
     *         if it requires a bearish candle.
     * @since 0.19
     */
    protected abstract boolean isBullish();

    private record Config(BarSeries series, RealBodyIndicator realBodyIndicator, Indicator<Num> bodyHeightIndicator,
            SMAIndicator averageBodyHeightIndicator, UpperShadowIndicator upperShadowIndicator,
            LowerShadowIndicator lowerShadowIndicator, int bodyAveragePeriod, Num bodyToAverageBodyRatioThreshold,
            Num upperShadowToBodyRatioThreshold, Num lowerShadowToBodyRatioThreshold, Num zero) {
    }
}
