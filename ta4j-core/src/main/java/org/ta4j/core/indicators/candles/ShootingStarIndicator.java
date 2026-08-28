/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * Shooting star candle indicator.
 *
 * <p>
 * A candle at index {@code i} is a shooting star when it has a short body, a
 * long upper shadow, a very short lower shadow, and opens strictly above the
 * previous candle's close:
 *
 * <pre>
 * body_i &lt; 0.5 * average(body[i-averagePeriod] ... body[i-1])
 * upperShadow_i &gt; 2.0 * average(body[i-averagePeriod] ... body[i-1])
 * lowerShadow_i &lt;= 0.1 * average(range[i-averagePeriod] ... range[i-1])
 * open_i &gt; close_(i-1)
 * </pre>
 *
 * The body and upper-shadow comparisons are <em>strict</em>; the lower-shadow
 * comparison is <em>inclusive</em> at its threshold, and the gap-up comparison
 * is <em>strict</em> (an open exactly equal to the previous close is not a
 * shooting star).
 *
 * <p>
 * This indicator evaluates only candle geometry; it does not evaluate trend or
 * direction context. A shooting star is traditionally interpreted as a bearish
 * reversal signal only after an uptrend — a context this indicator does not
 * own.
 *
 * @see <a href="https://www.investopedia.com/terms/s/shootingstar.asp">
 *      https://www.investopedia.com/terms/s/shootingstar.asp</a>
 */
public class ShootingStarIndicator extends CachedIndicator<Boolean> {

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
    public ShootingStarIndicator(final BarSeries series) {
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
    public ShootingStarIndicator(final BarSeries series, final int averagePeriod) {
        super(series);
        this.averagePeriod = averagePeriod;
        this.thresholds = new CandleThresholdSupport(series, averagePeriod);
        this.upperShadow = new UpperShadowIndicator(series);
        this.lowerShadow = new LowerShadowIndicator(series);
    }

    @Override
    protected Boolean calculate(final int index) {
        if (index - 1 < getBarSeries().getBeginIndex()) {
            return false;
        }
        final var bar = getBarSeries().getBar(index);
        final var priorClose = getBarSeries().getBar(index - 1).getClosePrice();
        return thresholds.isShortBody(index) && thresholds.isLongShadow(index, upperShadow)
                && thresholds.isShortShadow(index, lowerShadow) && bar.getOpenPrice().isGreaterThan(priorClose);
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod;
    }
}
