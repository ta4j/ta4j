/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Three black crows indicator.
 *
 * <p>
 * Matches the documented four-bar crow sequence at {@code index}: a bullish
 * white candle at {@code index - 3} followed by three black crows at
 * {@code index - 2}, {@code index - 1}, and {@code index}:
 *
 * <pre>
 * white candle at index - 3 is bullish
 * each crow at index k in {index - 2, index - 1, index} is bearish, has a
 *     very short lower shadow:
 *         lowerShadow(k) &lt;= 0.1 * priorAverageRange(k)
 * the first crow opens strictly below the white candle's high:
 *     open(index - 2) &lt; high(index - 3)
 * the second and third crows open within the previous body and close strictly
 *     below the previous close:
 *         open(k) &lt; open(k - 1) &amp;&amp; open(k) &gt; close(k - 1)
 *         close(k) &lt; close(k - 1)
 * </pre>
 *
 * <p>
 * The lower-shadow condition is inclusive, so a shadow exactly at the adaptive
 * threshold matches; the body-relative opens and declining closes are strict.
 * The short-shadow threshold is evaluated against the shared adaptive baseline
 * of the preceding {@code averagePeriod} candles.
 *
 * <p>
 * The default constructor uses a five-candle baseline ({@code averagePeriod} =
 * 5).
 *
 * <p>
 * The three crows are thresholded at offsets 0 through 2, so this indicator is
 * stable after {@code averagePeriod + 2} bars; the white candle at offset 3 is
 * geometry-only and needs no warm-up, only retained history. Before the
 * stability boundary, or when the retained history does not reach back to the
 * white candle, it returns {@code false}.
 *
 * <p>
 * Traditionally interpreted as a bearish reversal signal after an uptrend. This
 * indicator does not evaluate the preceding trend or any direction context; the
 * reversal context must be composed explicitly by the caller.
 *
 * @see <a href="http://www.investopedia.com/terms/t/three_black_crows.asp">
 *      http://www.investopedia.com/terms/t/three_black_crows.asp</a>
 */
public class ThreeBlackCrowsIndicator extends CandlePatternIndicator {

    /** Number of preceding candles averaged into the lower-shadow baseline. */
    private final int averagePeriod;

    private final transient Indicator<Num> lowerShadow;

    /**
     * Constructor with the default average period.
     *
     * @param series the bar series
     * @since 0.24.2
     */
    public ThreeBlackCrowsIndicator(BarSeries series) {
        super(CandleThresholdSupport.validateSeriesAndAveragePeriod(series,
                CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD),
                CandleThresholdSupport.forSeries(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD));
        this.averagePeriod = CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD;
        this.lowerShadow = thresholds.lowerShadow();
    }

    @Override
    int latestBaselineIndex(final int index) {
        return index - 2;
    }

    /**
     * Constructor with a custom average period.
     *
     * @param series        the bar series
     * @param averagePeriod the number of preceding candles averaged into the
     *                      lower-shadow baseline
     * @throws IllegalArgumentException if {@code averagePeriod} is below 1
     * @since 0.24.2
     */
    public ThreeBlackCrowsIndicator(BarSeries series, int averagePeriod) {
        super(CandleThresholdSupport.validateSeriesAndAveragePeriod(series, averagePeriod),
                CandleThresholdSupport.forSeries(series, averagePeriod));
        this.averagePeriod = averagePeriod;
        this.lowerShadow = thresholds.lowerShadow();
    }

    @Override
    protected Boolean calculate(int index) {
        BarSeries series = getBarSeries();
        if (index < getCountOfUnstableBars() || index - 3 < series.getBeginIndex()) {
            // We need 4 candles: 1 white, 3 black
            return false;
        }
        if (!Num.isFinite(lowerShadow.getValue(index - 2)) || !Num.isFinite(lowerShadow.getValue(index - 1))
                || !Num.isFinite(lowerShadow.getValue(index))) {
            return false;
        }

        Bar leadingBar = series.getBar(index - 3);
        if (!Num.isFinite(leadingBar.getOpenPrice()) || !Num.isFinite(leadingBar.getClosePrice())
                || !Num.isFinite(leadingBar.getHighPrice())) {
            return false;
        }

        return leadingBar.isBullish() && isBlackCrow(index - 2) && isBlackCrow(index - 1) && isBlackCrow(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod + 2;
    }

    /**
     * @param index the current bar/candle index
     * @return true if the current bar/candle is a black crow, false otherwise
     */
    private boolean isBlackCrow(int index) {
        Bar prevBar = getBarSeries().getBar(index - 1);
        Bar currBar = getBarSeries().getBar(index);
        if (currBar.isBearish()) {
            if (prevBar.isBullish()) {
                // First crow case
                return thresholds.isShortShadow(index, lowerShadow)
                        && currBar.getOpenPrice().isLessThan(prevBar.getHighPrice());
            } else {
                return thresholds.isShortShadow(index, lowerShadow) && isDeclining(index);
            }
        }
        return false;
    }

    /**
     * @param index the current bar/candle index
     * @return true if the current bar/candle is declining, false otherwise
     */
    private boolean isDeclining(int index) {
        Bar prevBar = getBarSeries().getBar(index - 1);
        Bar currBar = getBarSeries().getBar(index);
        final Num prevOpenPrice = prevBar.getOpenPrice();
        final Num prevClosePrice = prevBar.getClosePrice();
        final Num currOpenPrice = currBar.getOpenPrice();
        final Num currClosePrice = currBar.getClosePrice();

        // Opens within the body of the previous candle
        return currOpenPrice.isLessThan(prevOpenPrice) && currOpenPrice.isGreaterThan(prevClosePrice)
        // Closes below the previous close price
                && currClosePrice.isLessThan(prevClosePrice);
    }
}
