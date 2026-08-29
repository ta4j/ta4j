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
 * Three white soldiers indicator.
 *
 * <p>
 * Matches the documented three-candle three-white-soldiers morphology at
 * {@code index} for the candles at {@code index - 2}, {@code index - 1}, and
 * {@code index}:
 *
 * <pre>
 * all three candles are bullish
 * advancing closes (strict):
 *     close(index - 1) &gt; close(index - 2) &amp;&amp; close(index) &gt; close(index - 1)
 * the second and third opens sit within the previous real body (inclusive):
 *     open(index - 1) in [open(index - 2), close(index - 2)]
 *     open(index) in [open(index - 1), close(index - 1)]
 * each candle has a very short upper shadow (inclusive):
 *     upperShadow(k) &lt;= 0.1 * priorAverageRange(k) for k in {index - 2, index - 1, index}
 * </pre>
 *
 * <p>
 * The containment and upper-shadow conditions are inclusive, so an open exactly
 * on a body endpoint or a shadow exactly at the adaptive threshold matches; the
 * advancing closes are strict. The upper-shadow threshold is evaluated against
 * the shared adaptive baseline of the preceding {@code averagePeriod} candles.
 *
 * <p>
 * The default constructor uses a five-candle baseline ({@code averagePeriod} =
 * 5).
 *
 * <p>
 * The model is the canonical three-candle formation; it does not require a
 * preceding bearish candle. Earlier releases of this class forced a preceding
 * black candle and therefore a stricter four-bar variant; the class now
 * documents and implements the three-candle model.
 *
 * <p>
 * The three soldiers are thresholded at offsets 0 through 2, so this indicator
 * is stable after {@code averagePeriod + 2} bars. Before that boundary, or when
 * the retained history does not reach back to the first candle, it returns
 * {@code false}.
 *
 * <p>
 * Traditionally interpreted as a bullish reversal signal after a downtrend.
 * This indicator does not evaluate the preceding trend or any direction
 * context; the reversal context must be composed explicitly by the caller.
 *
 * @see <a href="http://www.investopedia.com/terms/t/three_white_soldiers.asp">
 *      http://www.investopedia.com/terms/t/three_white_soldiers.asp</a>
 */
public class ThreeWhiteSoldiersIndicator extends CandlePatternIndicator {

    /** Number of preceding candles averaged into the upper-shadow baseline. */
    private final int averagePeriod;

    private final transient Indicator<Num> upperShadow;

    /**
     * Constructor with the default average period.
     *
     * @param series the bar series
     * @since 0.24.2
     */
    public ThreeWhiteSoldiersIndicator(BarSeries series) {
        super(CandleThresholdSupport.validateSeriesAndAveragePeriod(series,
                CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD),
                CandleThresholdSupport.forSeries(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD));
        this.averagePeriod = CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD;
        this.upperShadow = thresholds.upperShadow();
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
     *                      upper-shadow baseline
     * @throws IllegalArgumentException if {@code averagePeriod} is below 1
     * @since 0.24.2
     */
    public ThreeWhiteSoldiersIndicator(BarSeries series, int averagePeriod) {
        super(CandleThresholdSupport.validateSeriesAndAveragePeriod(series, averagePeriod),
                CandleThresholdSupport.forSeries(series, averagePeriod));
        this.averagePeriod = averagePeriod;
        this.upperShadow = thresholds.upperShadow();
    }

    @Override
    protected Boolean calculate(int index) {
        BarSeries series = getBarSeries();
        if (index < getCountOfUnstableBars() || index - 2 < series.getBeginIndex()) {
            return false;
        }
        Bar firstBar = series.getBar(index - 2);
        Bar secondBar = series.getBar(index - 1);
        Bar thirdBar = series.getBar(index);
        if (!Num.isFinite(firstBar.getOpenPrice()) || !Num.isFinite(firstBar.getClosePrice())
                || !Num.isFinite(secondBar.getOpenPrice()) || !Num.isFinite(secondBar.getClosePrice())
                || !Num.isFinite(thirdBar.getOpenPrice()) || !Num.isFinite(thirdBar.getClosePrice())) {
            return false;
        }
        if (!firstBar.isBullish() || !secondBar.isBullish() || !thirdBar.isBullish()) {
            return false;
        }
        if (!Num.isFinite(upperShadow.getValue(index - 2)) || !Num.isFinite(upperShadow.getValue(index - 1))
                || !Num.isFinite(upperShadow.getValue(index))) {
            return false;
        }
        return secondBar.getClosePrice().isGreaterThan(firstBar.getClosePrice())
                && thirdBar.getClosePrice().isGreaterThan(secondBar.getClosePrice())
                && openWithinBody(secondBar, firstBar) && openWithinBody(thirdBar, secondBar)
                && thresholds.isShortShadow(index - 2, upperShadow) && thresholds.isShortShadow(index - 1, upperShadow)
                && thresholds.isShortShadow(index, upperShadow);
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod + 2;
    }

    /**
     * @param bar   the candle whose open is checked
     * @param prior the previous candle whose real body is the containment window
     * @return true if the open of {@code bar} lies within the real body of
     *         {@code prior}, endpoints included
     */
    private static boolean openWithinBody(Bar bar, Bar prior) {
        Num open = bar.getOpenPrice();
        Num priorBodyBottom = prior.getOpenPrice().min(prior.getClosePrice());
        Num priorBodyTop = prior.getOpenPrice().max(prior.getClosePrice());
        return open.isGreaterThanOrEqual(priorBodyBottom) && open.isLessThanOrEqual(priorBodyTop);
    }
}
