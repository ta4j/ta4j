/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
        this(series, DEFAULT_BODY_AVERAGE_PERIOD, DEFAULT_BODY_TO_AVERAGE_BODY_RATIO,
                DEFAULT_UPPER_SHADOW_TO_BODY_RATIO, DEFAULT_LOWER_SHADOW_TO_BODY_RATIO);
    }

    AbstractMarubozuIndicator(final BarSeries series, final int bodyAveragePeriod, final double bodyToAverageBodyRatio,
            final double upperShadowToBodyRatio, final double lowerShadowToBodyRatio) {
        super(Objects.requireNonNull(series, "series must not be null"));
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
        this.bodyAveragePeriod = bodyAveragePeriod;
        this.realBodyIndicator = new RealBodyIndicator(series);
        this.bodyHeightIndicator = UnaryOperationIndicator.abs(this.realBodyIndicator);
        this.averageBodyHeightIndicator = new SMAIndicator(this.bodyHeightIndicator, bodyAveragePeriod);
        this.upperShadowIndicator = new UpperShadowIndicator(series);
        this.lowerShadowIndicator = new LowerShadowIndicator(series);

        final NumFactory numFactory = series.numFactory();
        this.bodyToAverageBodyRatioThreshold = numFactory.numOf(bodyToAverageBodyRatio);
        this.upperShadowToBodyRatioThreshold = numFactory.numOf(upperShadowToBodyRatio);
        this.lowerShadowToBodyRatioThreshold = numFactory.numOf(lowerShadowToBodyRatio);
        this.zero = numFactory.zero();
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
}
