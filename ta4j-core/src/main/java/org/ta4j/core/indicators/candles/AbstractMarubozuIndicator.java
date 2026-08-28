/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;

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
abstract class AbstractMarubozuIndicator extends CachedIndicator<Boolean> {

    /**
     * The number of preceding candles averaged into the body and range baselines.
     */
    private final int averagePeriod;

    /** Shared causal threshold evaluation against the preceding window. */
    private final transient CandleThresholdSupport thresholds;

    /** The current candle's upper shadow. */
    private final transient UpperShadowIndicator upperShadow;

    /** The current candle's lower shadow. */
    private final transient LowerShadowIndicator lowerShadow;

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
        super(series);
        this.averagePeriod = averagePeriod;
        this.thresholds = new CandleThresholdSupport(series, averagePeriod);
        this.upperShadow = new UpperShadowIndicator(series);
        this.lowerShadow = new LowerShadowIndicator(series);
    }

    @Override
    protected Boolean calculate(final int index) {
        return thresholds.isLongBody(index) && thresholds.isShortShadow(index, upperShadow)
                && thresholds.isShortShadow(index, lowerShadow) && hasExpectedDirection(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod;
    }

    private boolean hasExpectedDirection(final int index) {
        final Bar bar = getBarSeries().getBar(index);
        return isBullish() ? bar.isBullish() : bar.isBearish();
    }

    /**
     * @return {@code true} if the Marubozu requires a bullish candle (close &gt;
     *         open), {@code false} if it requires a bearish candle (open &gt;
     *         close).
     * @since 0.19
     */
    protected abstract boolean isBullish();
}
