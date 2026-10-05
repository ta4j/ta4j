/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Shared logic for Marubozu-style candlestick pattern indicators.
 *
 * <p>
 * A Marubozu candle at index {@code i} is a directional candle whose body is
 * strictly greater than 1.0 (CandleThresholdSupport.LONG_BODY_FACTOR) times the
 * average body of the {@code averagePeriod} candles preceding it, and whose
 * upper and lower shadows are each at most 0.1
 * (CandleThresholdSupport.SHORT_SHADOW_RANGE_FACTOR) times the average high-low
 * range of the same preceding window:
 *
 * <pre>
 * body_i &gt; 1.0 * average(body[i-averagePeriod] ... body[i-1])
 * upperShadow_i &lt;= 0.1 * average(range[i-averagePeriod] ... range[i-1])
 * lowerShadow_i &lt;= 0.1 * average(range[i-averagePeriod] ... range[i-1])
 * </pre>
 *
 * The body comparison is <em>strict</em>; both shadow comparisons are
 * <em>inclusive</em> at the threshold. Concrete subclasses decide whether the
 * body must be bullish (close &gt; open) or bearish (open &gt; close). A candle
 * with a zero body (open equals close) satisfies neither direction.
 *
 * <p>
 * This indicator evaluates only candle geometry; it does not evaluate trend or
 * direction context. A Marubozu is traditionally interpreted as a sign of
 * strong one-direction momentum, but whether it predicts continuation depends
 * on the context the caller composes around it.
 *
 * @since 0.19
 */
abstract class AbstractMarubozuIndicator extends CandlePatternIndicator {

    /**
     * The number of preceding candles averaged into the body and range baselines.
     */
    private final int averagePeriod;

    /**
     * The custom body threshold, or {@code null} when this indicator uses the
     * canonical shared thresholds.
     */
    private final Num bodyToAverageBodyRatio;

    /** The custom upper-shadow-to-body threshold. */
    private final Num upperShadowToBodyRatio;

    /** The custom lower-shadow-to-body threshold. */
    private final Num lowerShadowToBodyRatio;

    /** The current candle's upper shadow, shared from the interned support. */
    private final transient Indicator<Num> upperShadow;

    /** The current candle's lower shadow, shared from the interned support. */
    private final transient Indicator<Num> lowerShadow;

    /**
     * Constructor with the recommended default average period of 5 candles.
     *
     * @param series the bar series
     */
    AbstractMarubozuIndicator(final BarSeries series) {
        this(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD);
    }

    /**
     * Constructor with a custom average period.
     *
     * @param series        the bar series
     * @param averagePeriod the number of preceding candles averaged into each
     *                      baseline; must be at least 1
     * @throws IllegalArgumentException if {@code averagePeriod} is below 1
     */
    AbstractMarubozuIndicator(final BarSeries series, final int averagePeriod) {
        super(series, CandleThresholdSupport.forSeries(series, averagePeriod));
        this.averagePeriod = averagePeriod;
        this.upperShadow = thresholds.upperShadow();
        this.lowerShadow = thresholds.lowerShadow();
        this.bodyToAverageBodyRatio = null;
        this.upperShadowToBodyRatio = null;
        this.lowerShadowToBodyRatio = null;
    }

    /**
     * Compatibility constructor with custom body and body-relative shadow
     * thresholds.
     *
     * @param series                 the bar series
     * @param averagePeriod          the number of preceding candles averaged into
     *                               the body baseline; must be at least 1
     * @param bodyToAverageBodyRatio the strictly exceeded body-to-average-body
     *                               ratio; must be finite and positive
     * @param upperShadowToBodyRatio the inclusive upper-shadow-to-body ratio; must
     *                               be finite and non-negative
     * @param lowerShadowToBodyRatio the inclusive lower-shadow-to-body ratio; must
     *                               be finite and non-negative
     * @throws IllegalArgumentException if a threshold is outside its valid range
     */
    AbstractMarubozuIndicator(final BarSeries series, final int averagePeriod, final double bodyToAverageBodyRatio,
            final double upperShadowToBodyRatio, final double lowerShadowToBodyRatio) {
        this(validatedConfiguration(series, averagePeriod, bodyToAverageBodyRatio, upperShadowToBodyRatio,
                lowerShadowToBodyRatio));
    }

    private AbstractMarubozuIndicator(final Configuration configuration) {
        super(configuration.series(),
                CandleThresholdSupport.forSeries(configuration.series(), configuration.averagePeriod()));
        this.averagePeriod = configuration.averagePeriod();
        this.bodyToAverageBodyRatio = configuration.bodyToAverageBodyRatio();
        this.upperShadowToBodyRatio = configuration.upperShadowToBodyRatio();
        this.lowerShadowToBodyRatio = configuration.lowerShadowToBodyRatio();
        this.upperShadow = thresholds.upperShadow();
        this.lowerShadow = thresholds.lowerShadow();
    }

    @Override
    protected Boolean calculate(final int index) {
        if (bodyToAverageBodyRatio == null) {
            return thresholds.isLongBody(index) && thresholds.isShortShadow(index, upperShadow)
                    && thresholds.isShortShadow(index, lowerShadow) && hasExpectedDirection(index);
        }
        return thresholds.isLongBody(index, bodyToAverageBodyRatio)
                && thresholds.isShortShadowRelativeToBody(index, upperShadow, upperShadowToBodyRatio)
                && thresholds.isShortShadowRelativeToBody(index, lowerShadow, lowerShadowToBodyRatio)
                && hasExpectedDirection(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod;
    }

    private boolean hasExpectedDirection(final int index) {
        final Bar bar = getBarSeries().getBar(index);
        return isBullish() ? bar.isBullish() : bar.isBearish();
    }

    private static Configuration validatedConfiguration(final BarSeries series, final int averagePeriod,
            final double bodyToAverageBodyRatio, final double upperShadowToBodyRatio,
            final double lowerShadowToBodyRatio) {
        final BarSeries validatedSeries = CandleThresholdSupport.validateSeriesAndAveragePeriod(series, averagePeriod);
        validatePositiveFactor(bodyToAverageBodyRatio, "bodyToAverageBodyRatio");
        validateNonNegativeFactor(upperShadowToBodyRatio, "upperShadowToBodyRatio");
        validateNonNegativeFactor(lowerShadowToBodyRatio, "lowerShadowToBodyRatio");

        final NumFactory numFactory = validatedSeries.numFactory();
        return new Configuration(validatedSeries, averagePeriod, numFactory.numOf(bodyToAverageBodyRatio),
                numFactory.numOf(upperShadowToBodyRatio == 0d ? 0d : upperShadowToBodyRatio),
                numFactory.numOf(lowerShadowToBodyRatio == 0d ? 0d : lowerShadowToBodyRatio));
    }

    private static void validatePositiveFactor(final double factor, final String name) {
        if (!Double.isFinite(factor) || factor <= 0d) {
            throw new IllegalArgumentException(name + " must be finite and > 0, but was: " + factor);
        }
    }

    private static void validateNonNegativeFactor(final double factor, final String name) {
        if (!Double.isFinite(factor) || factor < 0d) {
            throw new IllegalArgumentException(name + " must be finite and >= 0, but was: " + factor);
        }
    }

    private record Configuration(BarSeries series, int averagePeriod, Num bodyToAverageBodyRatio,
            Num upperShadowToBodyRatio, Num lowerShadowToBodyRatio) {
    }

    /**
     * @return {@code true} if the Marubozu requires a bullish candle (close &gt;
     *         open), {@code false} if it requires a bearish candle (open &gt;
     *         close).
     * @since 0.19
     */

    protected abstract boolean isBullish();
}
