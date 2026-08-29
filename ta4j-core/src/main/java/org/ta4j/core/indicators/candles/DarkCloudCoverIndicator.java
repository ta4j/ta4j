/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Dark cloud cover pattern indicator.
 *
 * <p>
 * Detected at index {@code i} when the two-candle sequence ending at {@code i}
 * satisfies
 *
 * <pre>
 * first.isBullish() &amp;&amp; isLongBody(i - 1) &amp;&amp; second.isBearish() &amp;&amp; open(i) &gt; high(i - 1) &amp;&amp; close(i) &gt; open(i - 1)
 *         &amp;&amp; close(i) &lt;= bodyTop(i - 1) - penetration * body(i - 1)
 * </pre>
 *
 * where {@code bodyTop = max(open, close)}, {@code body = |close - open|}, and
 * {@code isLongBody} requires a body strictly greater than the
 * {@code averagePeriod}-bar prior average body (factor 1.0). The gap above the
 * prior high is strict ({@code >}), while the penetration of the prior body is
 * inclusive ({@code <=}): a close exactly on the penetration threshold still
 * matches. The close must also remain strictly above the first candle's open
 * ({@code >}): a close at or below it engulfs the first body and is not a dark
 * cloud cover.
 *
 * <p>
 * This indicator does not evaluate trend or direction context: it reports the
 * two-candle morphology only. The conventional bearish reversal interpretation
 * after an uptrend is context this class does not own.
 *
 * @see <a href="https://www.investopedia.com/terms/d/darkcloud.asp">
 *      https://www.investopedia.com/terms/d/darkcloud.asp</a>
 * @since 0.22.3
 */
public class DarkCloudCoverIndicator extends CandlePatternIndicator {

    private static final double DEFAULT_PENETRATION = 0.5;

    private final int averagePeriod;
    private final double penetration;
    private final transient Num penetrationValue;

    /**
     * Constructor with the default period of 5 and a penetration of 0.5.
     *
     * @param series the bar series
     */
    public DarkCloudCoverIndicator(final BarSeries series) {
        super(CandleThresholdSupport.validateSeriesAndAveragePeriodAndPenetration(series,
                CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD, DEFAULT_PENETRATION),
                CandleThresholdSupport.forSeries(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD));
        this.averagePeriod = CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD;
        this.penetration = DEFAULT_PENETRATION;
        this.penetrationValue = getBarSeries().numFactory().numOf(penetration);
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
     * @throws IllegalArgumentException if {@code averagePeriod} is below 1 or
     *                                  {@code penetration} is not finite, is not
     *                                  positive, or exceeds 1
     * @since 0.24.2
     */
    public DarkCloudCoverIndicator(final BarSeries series, final int averagePeriod, final double penetration) {
        super(CandleThresholdSupport.validateSeriesAndAveragePeriodAndPenetration(series, averagePeriod, penetration),
                CandleThresholdSupport.forSeries(series, averagePeriod));
        this.averagePeriod = averagePeriod;
        this.penetration = penetration;
        this.penetrationValue = getBarSeries().numFactory().numOf(penetration);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index - 1 < getBarSeries().getBeginIndex()) {
            // Dark cloud cover is a 2-candle pattern
            return false;
        }
        Bar firstBar = getBarSeries().getBar(index - 1);
        Bar secondBar = getBarSeries().getBar(index);
        if (firstBar.isBullish() && thresholds.isLongBody(index - 1) && secondBar.isBearish()
                && Num.isFinite(firstBar.getHighPrice()) && Num.isFinite(secondBar.getOpenPrice())
                && secondBar.getOpenPrice().isGreaterThan(firstBar.getHighPrice())) {
            Num firstBodyTop = firstBar.getOpenPrice().max(firstBar.getClosePrice());
            Num firstBody = firstBar.getClosePrice().minus(firstBar.getOpenPrice()).abs();
            Num requiredClose = firstBodyTop.minus(firstBody.multipliedBy(penetrationValue));
            return secondBar.getClosePrice().isGreaterThan(firstBar.getOpenPrice())
                    && secondBar.getClosePrice().isLessThanOrEqual(requiredClose);
        }
        return false;
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod + 1;
    }

}
