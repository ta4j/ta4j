/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Morning star candle indicator.
 *
 * <p>
 * Matches the documented three-candle morning star morphology at {@code index}
 * for the candles at {@code index - 2} (first), {@code index - 1} (star), and
 * {@code index} (third):
 *
 * <pre>
 * first is bearish with a long body: body(first) &gt; priorAverageBody(first)
 * star has a short body and its real body gaps strictly below the first body:
 * body(star) &lt; 0.5 * priorAverageBody(star) bodyTop(star) &lt;
 * bodyBottom(first) third is bullish, has a long body, and its close penetrates
 * the first body inclusively: body(third) &gt; priorAverageBody(third)
 * close(third) &gt;= bodyBottom(first) + penetration * body(first)
 * </pre>
 *
 * <p>
 * {@code body} is the absolute close-to-open difference, {@code bodyTop} and
 * {@code bodyBottom} are the higher and lower of a candle's open and close
 * prices, and the body thresholds are evaluated against the shared adaptive
 * baselines of the preceding {@code averagePeriod} candles. The long-body,
 * short-body, and real-body gap conditions are strict; the penetration
 * condition is inclusive, so a close exactly at the penetration level matches.
 *
 * <p>
 * The default constructor uses a five-candle baseline ({@code averagePeriod} =
 * 5) and a {@code penetration} of 0.5.
 *
 * <p>
 * This indicator is stable after {@code averagePeriod + 2} bars. Before that
 * boundary, or when the retained history does not reach back to the first
 * candle, it returns {@code false}.
 *
 * <p>
 * Traditionally interpreted as a bullish reversal signal after a downtrend.
 * This indicator does not evaluate the preceding trend or any direction
 * context; the reversal context must be composed explicitly by the caller.
 *
 * @see <a href="https://www.investopedia.com/terms/m/morningstar.asp">
 *      https://www.investopedia.com/terms/m/morningstar.asp</a>
 * @since 0.22.2
 */
public class MorningStarIndicator extends CandlePatternIndicator {

    /** Default penetration of the first body required by the third close. */
    private static final double DEFAULT_PENETRATION = 0.5;

    private final int averagePeriod;
    private final double penetration;

    private final transient Num penetrationFactor;
    private final transient Num oneMinusPenetrationFactor;

    /**
     * Constructor with the default average period and penetration.
     *
     * @param series the bar series
     */
    public MorningStarIndicator(final BarSeries series) {
        super(CandleThresholdSupport.validateSeriesAndAveragePeriodAndPenetration(series,
                CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD, DEFAULT_PENETRATION),
                CandleThresholdSupport.forSeries(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD));
        this.averagePeriod = CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD;
        this.penetration = DEFAULT_PENETRATION;
        this.penetrationFactor = getBarSeries().numFactory().numOf(penetration);
        this.oneMinusPenetrationFactor = getBarSeries().numFactory().one().minus(penetrationFactor);
    }

    @Override
    int latestBaselineIndex(final int index) {
        return index - 2;
    }

    /**
     * Constructor with a custom average period and penetration.
     *
     * @param series        the bar series
     * @param averagePeriod the number of preceding candles averaged into each body
     *                      baseline
     * @param penetration   the fraction of the first body the third close must
     *                      penetrate
     * @throws IllegalArgumentException if {@code averagePeriod} is below 1 or above
     *                                  {@link CandleThresholdSupport#MAX_AVERAGE_PERIOD},
     *                                  or if {@code penetration} is not finite, is
     *                                  not positive, or exceeds 1
     * @since 0.24.2
     */
    public MorningStarIndicator(final BarSeries series, final int averagePeriod, final double penetration) {
        super(CandleThresholdSupport.validateSeriesAndAveragePeriodAndPenetration(series, averagePeriod, penetration),
                CandleThresholdSupport.forSeries(series, averagePeriod));
        this.averagePeriod = averagePeriod;
        this.penetration = penetration;
        this.penetrationFactor = getBarSeries().numFactory().numOf(penetration);
        this.oneMinusPenetrationFactor = getBarSeries().numFactory().one().minus(penetrationFactor);
    }

    @Override
    protected Boolean calculate(int index) {
        BarSeries series = getBarSeries();
        if (index < getCountOfUnstableBars() || index - 2 < series.getBeginIndex()) {
            return false;
        }
        Bar firstBar = series.getBar(index - 2);
        Bar starBar = series.getBar(index - 1);
        Bar thirdBar = series.getBar(index);
        Num firstBodyBottom = bodyBottom(firstBar);
        Num firstBodyTop = bodyTop(firstBar);
        Num penetrationLevel = CandleThresholdSupport.weightedPoint(firstBodyTop, penetrationFactor, firstBodyBottom,
                oneMinusPenetrationFactor);
        Num thirdClose = thirdBar.getClosePrice();
        // Signed zero is normalized at the strict gap and the inclusive
        // penetration boundary: DoubleNum orders -0.0 below +0.0, so two
        // numerically equal zero endpoints must never form a gap and must
        // still satisfy the inclusive penetration level.
        return firstBar.isBearish() && thresholds.isLongBody(index - 2) && thresholds.isShortBody(index - 1)
                && !(bodyTop(starBar).isZero() && firstBodyBottom.isZero())
                && bodyTop(starBar).isLessThan(firstBodyBottom) && thirdBar.isBullish()
                && Num.isFinite(thirdBar.getOpenPrice()) && Num.isFinite(thirdClose) && thresholds.isLongBody(index)
                && (thirdClose.isGreaterThanOrEqual(penetrationLevel)
                        || (thirdClose.isZero() && penetrationLevel.isZero()));
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod + 2;
    }

    /**
     * @param bar the candle
     * @return the lower of the open and close prices
     */
    private static Num bodyBottom(Bar bar) {
        return bar.getOpenPrice().min(bar.getClosePrice());
    }

    /**
     * @param bar the candle
     * @return the higher of the open and close prices
     */
    private static Num bodyTop(Bar bar) {
        return bar.getOpenPrice().max(bar.getClosePrice());
    }
}
