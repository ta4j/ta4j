/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;

/**
 * Hanging man candle indicator.
 *
 * <p>
 * A candle at index {@code i} is a hanging man when it has a short body, a long
 * lower shadow, a very short upper shadow, and its body top sits near the
 * previous candle's high:
 *
 * <pre>
 * body_i &lt; 0.5 * average(body[i-averagePeriod] ... body[i-1])
 * lowerShadow_i &gt; 2.0 * average(body[i-averagePeriod] ... body[i-1])
 * upperShadow_i &lt;= 0.1 * average(range[i-averagePeriod] ... range[i-1])
 * |max(open_i, close_i) - high_(i-1)| &lt;= 0.1 * average(range[i-averagePeriod] ... range[i-1])
 * </pre>
 *
 * The body and lower-shadow comparisons are <em>strict</em>; the upper-shadow
 * and body-location comparisons are <em>inclusive</em> at their thresholds.
 *
 * <p>
 * This indicator evaluates only candle geometry; it does not evaluate trend or
 * direction context. A hanging man is traditionally interpreted as a bearish
 * reversal signal only after an uptrend — a context this indicator does not
 * own.
 *
 * @see <a href="https://www.investopedia.com/terms/h/hangingman.asp">
 *      https://www.investopedia.com/terms/h/hangingman.asp</a>
 */
public class HangingManIndicator extends CachedIndicator<Boolean> {

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
    public HangingManIndicator(final BarSeries series) {
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
    public HangingManIndicator(final BarSeries series, final int averagePeriod) {
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
        final var bodyTop = bar.getOpenPrice().max(bar.getClosePrice());
        final var priorHigh = getBarSeries().getBar(index - 1).getHighPrice();
        return thresholds.isShortBody(index) && thresholds.isLongShadow(index, lowerShadow)
                && thresholds.isShortShadow(index, upperShadow)
                && !bodyTop.minus(priorHigh)
                        .abs()
                        .isGreaterThan(thresholds.priorAverageRange()
                                .getValue(index)
                                .multipliedBy(
                                        getBarSeries().numFactory().numOf(CandleThresholdSupport.NEAR_RANGE_FACTOR)));
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod;
    }
}
