/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Hammer candle indicator.
 *
 * <p>
 * A candle at index {@code i} is a hammer when it has a short body, a long
 * lower shadow, a very short upper shadow, and its body bottom sits near the
 * previous candle's low:
 *
 * <pre>
 * body_i &lt; 0.5 * average(body[i-averagePeriod] ... body[i-1])
 * lowerShadow_i &gt; 2.0 * average(body[i-averagePeriod] ... body[i-1])
 * upperShadow_i &lt;= 0.1 * average(range[i-averagePeriod] ... range[i-1])
 * |min(open_i, close_i) - low_(i-1)| &lt;= 0.1 * average(range[i-averagePeriod] ... range[i-1])
 * </pre>
 *
 * The body and lower-shadow comparisons are <em>strict</em>; the upper-shadow
 * and body-location comparisons are <em>inclusive</em> at their thresholds.
 *
 * <p>
 * This indicator evaluates only candle geometry; it does not evaluate trend or
 * direction context. A hammer is traditionally interpreted as a bullish
 * reversal signal only after a downtrend — a context this indicator does not
 * own.
 *
 * @see <a href="https://www.investopedia.com/terms/h/hammer.asp">
 *      https://www.investopedia.com/terms/h/hammer.asp</a>
 */
public class HammerIndicator extends CandlePatternIndicator {

    /**
     * The number of preceding candles averaged into the body and range baselines.
     */
    private final int averagePeriod;

    /** The current candle's upper shadow, shared from the interned support. */
    private final transient Indicator<Num> upperShadow;

    /** The current candle's lower shadow, shared from the interned support. */
    private final transient Indicator<Num> lowerShadow;

    /**
     * Constructor with the recommended default average period of 5 candles.
     *
     * @param series the bar series
     */
    public HammerIndicator(final BarSeries series) {
        this(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD);
    }

    /**
     * Constructor with a custom average period.
     *
     * @param series        the bar series
     * @param averagePeriod the number of preceding candles averaged into each
     *                      baseline; must be at least 1
     * @throws IllegalArgumentException if {@code averagePeriod} is below 1
     * @since 0.24.2
     */
    public HammerIndicator(final BarSeries series, final int averagePeriod) {
        super(series, CandleThresholdSupport.forSeries(series, averagePeriod));
        this.averagePeriod = averagePeriod;
        this.upperShadow = thresholds.upperShadow();
        this.lowerShadow = thresholds.lowerShadow();
    }

    @Override
    protected Boolean calculate(final int index) {
        if (index - 1 < getBarSeries().getBeginIndex()) {
            return false;
        }
        final var bar = getBarSeries().getBar(index);
        final var priorLow = getBarSeries().getBar(index - 1).getLowPrice();
        if (!Num.isFinite(priorLow)) {
            return false;
        }
        final var bodyBottom = bar.getOpenPrice().min(bar.getClosePrice());
        return thresholds.isShortBody(index) && thresholds.isLongShadow(index, lowerShadow)
                && thresholds.isShortShadow(index, upperShadow)
                && !bodyBottom.minus(priorLow)
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
