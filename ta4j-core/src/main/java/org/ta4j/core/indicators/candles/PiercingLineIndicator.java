/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Piercing line pattern indicator.
 *
 * <p>
 * Detected at index {@code i} when the two-candle sequence ending at {@code i}
 * satisfies
 *
 * <pre>
 * first.isBearish() &amp;&amp; isLongBody(i - 1) &amp;&amp; second.isBullish() &amp;&amp; open(i) &lt; low(i - 1) &amp;&amp; close(i) &lt; open(i - 1)
 *         &amp;&amp; close(i) &gt;= bodyBottom(i - 1) + penetration * body(i - 1)
 * </pre>
 *
 * where {@code bodyBottom = min(open, close)}, {@code body = |close - open|},
 * and {@code isLongBody} requires a body strictly greater than the
 * {@code averagePeriod}-bar prior average body (factor 1.0). The gap below the
 * prior low is strict ({@code <}), while the penetration of the prior body is
 * inclusive ({@code >=}): a close exactly on the penetration threshold still
 * matches. The close must also remain strictly below the first candle's open
 * ({@code <}): a close at or above it engulfs the first body and is not a
 * piercing line.
 *
 * <p>
 * The default {@code averagePeriod} is
 * {@value CandleThresholdSupport#DEFAULT_AVERAGE_PERIOD} bars and the default
 * {@code penetration} is {@value #DEFAULT_PENETRATION}.
 *
 * <p>
 * This indicator does not evaluate trend or direction context: it reports the
 * two-candle morphology only. The conventional bullish reversal interpretation
 * after a downtrend is context this class does not own.
 *
 * @see <a href="https://www.investopedia.com/terms/p/piercing-pattern.asp">
 *      https://www.investopedia.com/terms/p/piercing-pattern.asp</a>
 * @since 0.22.3
 */
public class PiercingLineIndicator extends CandlePatternIndicator {

    private static final double DEFAULT_PENETRATION = 0.5;

    private final int averagePeriod;
    private final double penetration;
    private final transient Num penetrationValue;
    private final transient Num oneMinusPenetrationValue;

    /**
     * Constructor with the default period of 5 and a penetration of 0.5.
     *
     * @param series the bar series
     */
    public PiercingLineIndicator(final BarSeries series) {
        super(CandleThresholdSupport.validateSeriesAndAveragePeriodAndPartialPenetration(series,
                CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD, DEFAULT_PENETRATION),
                CandleThresholdSupport.forSeries(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD));
        this.averagePeriod = CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD;
        this.penetration = DEFAULT_PENETRATION;
        this.penetrationValue = getBarSeries().numFactory().numOf(penetration);
        this.oneMinusPenetrationValue = getBarSeries().numFactory().one().minus(penetrationValue);
    }

    @Override
    int latestBaselineIndex(final int index) {
        return index - 1;
    }

    /**
     * Constructor.
     *
     * @param series        the bar series
     * @param averagePeriod the number of preceding candles averaged into the
     *                      long-body baseline (at least 1)
     * @param penetration   the fraction of the first body the second close must
     *                      penetrate, exclusive 0 and below 1
     * @throws IllegalArgumentException if {@code averagePeriod} is below 1 or
     *                                  {@code penetration} is not finite, is not
     *                                  positive, or is not below 1
     * @since 0.24.2
     */
    public PiercingLineIndicator(final BarSeries series, final int averagePeriod, final double penetration) {
        super(CandleThresholdSupport.validateSeriesAndAveragePeriodAndPartialPenetration(series, averagePeriod,
                penetration), CandleThresholdSupport.forSeries(series, averagePeriod));
        this.averagePeriod = averagePeriod;
        this.penetration = penetration;
        this.penetrationValue = getBarSeries().numFactory().numOf(penetration);
        this.oneMinusPenetrationValue = getBarSeries().numFactory().one().minus(penetrationValue);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index - 1 < getBarSeries().getBeginIndex()) {
            // Piercing line is a 2-candle pattern
            return false;
        }
        Bar firstBar = getBarSeries().getBar(index - 1);
        Bar secondBar = getBarSeries().getBar(index);
        Num firstLow = firstBar.getLowPrice();
        Num secondOpen = secondBar.getOpenPrice();
        // Signed zero is normalized in the strict clause: DoubleNum orders
        // -0.0 below +0.0, so two numerically zero endpoints must not count
        // as a strict gap. The finite guards are evaluated first so that
        // missing prices short-circuit before any comparison.
        if (firstBar.isBearish() && thresholds.isLongBody(index - 1) && secondBar.isBullish() && Num.isFinite(firstLow)
                && Num.isFinite(secondOpen) && !(secondOpen.isZero() && firstLow.isZero())
                && secondOpen.isLessThan(firstLow)) {
            Num firstBodyBottom = firstBar.getOpenPrice().min(firstBar.getClosePrice());
            Num firstBodyTop = firstBar.getOpenPrice().max(firstBar.getClosePrice());
            Num secondClose = secondBar.getClosePrice();
            Num requiredClose = CandleThresholdSupport.weightedPoint(firstBodyBottom, oneMinusPenetrationValue,
                    firstBodyTop, penetrationValue);
            return !(secondClose.isZero() && firstBodyTop.isZero()) && secondClose.isLessThan(firstBodyTop)
                    && secondClose.isGreaterThanOrEqual(requiredClose);
        }
        return false;
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod + 1;
    }

}
