/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

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
public class HangingManIndicator extends CandlePatternIndicator {

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
     * @since 0.24.2
     */
    public HangingManIndicator(final BarSeries series, final int averagePeriod) {
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
        final Bar bar = getBarSeries().getBar(index);
        final Num priorHigh = getBarSeries().getBar(index - 1).getHighPrice();
        if (!Num.isFinite(priorHigh)) {
            return false;
        }
        final Num open = bar.getOpenPrice();
        final Num close = bar.getClosePrice();
        if (open == null || close == null || !Num.isFinite(open) || !Num.isFinite(close)) {
            return false;
        }
        final Num bodyTop = open.max(close);
        return thresholds.isShortBody(index) && thresholds.isLongShadow(index, lowerShadow)
                && thresholds.isShortShadow(index, upperShadow) && thresholds.isNear(index, bodyTop, priorHigh);
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod;
    }
}
