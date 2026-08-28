/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Inverted hammer candle indicator.
 *
 * <p>
 * A candle at index {@code i} is an inverted hammer when it has a short body, a
 * long upper shadow, a very short lower shadow, and opens strictly below the
 * previous candle's close:
 *
 * <pre>
 * body_i &lt; 0.5 * average(body[i-averagePeriod] ... body[i-1])
 * upperShadow_i &gt; 2.0 * average(body[i-averagePeriod] ... body[i-1])
 * lowerShadow_i &lt;= 0.1 * average(range[i-averagePeriod] ... range[i-1])
 * open_i &lt; close_(i-1)
 * </pre>
 *
 * The body and upper-shadow comparisons are <em>strict</em>; the lower-shadow
 * comparison is <em>inclusive</em> at its threshold, and the gap-down
 * comparison is <em>strict</em> (an open exactly equal to the previous close is
 * not an inverted hammer).
 *
 * <p>
 * This indicator evaluates only candle geometry; it does not evaluate trend or
 * direction context. An inverted hammer is traditionally interpreted as a
 * bullish reversal signal only after a downtrend — a context this indicator
 * does not own.
 * 
 * @see <a href="https://www.investopedia.com/terms/i/invertedhammer.asp">
 *      https://www.investopedia.com/terms/i/invertedhammer.asp</a>
 */
public class InvertedHammerIndicator extends CandlePatternIndicator {

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
    public InvertedHammerIndicator(final BarSeries series) {
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
    public InvertedHammerIndicator(final BarSeries series, final int averagePeriod) {
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
        final var priorClose = getBarSeries().getBar(index - 1).getClosePrice();
        return thresholds.isShortBody(index) && thresholds.isLongShadow(index, upperShadow)
                && thresholds.isShortShadow(index, lowerShadow) && bar.getOpenPrice().isLessThan(priorClose);
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod;
    }
}
