/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Bullish Harami pattern indicator.
 *
 * <p>
 * Detected at index {@code i} when the two-candle sequence ending at {@code i}
 * satisfies
 *
 * <pre>
 * prev.isBearish() &amp;&amp; isLongBody(i - 1) &amp;&amp; curr.isBullish() &amp;&amp; isShortBody(i) &amp;&amp; bodyTop(i) &lt;= bodyTop(i - 1)
 *         &amp;&amp; bodyBottom(i) &gt;= bodyBottom(i - 1)
 * </pre>
 *
 * where {@code bodyTop = max(open, close)} and
 * {@code bodyBottom = min(open, close)}, {@code isLongBody} requires a body
 * strictly greater than the {@code averagePeriod}-bar prior average body
 * (factor 1.0) and {@code isShortBody} requires a body strictly smaller than it
 * (factor 0.5). Containment is inclusive: the current body may share an
 * endpoint with the previous body and still match.
 *
 * <p>
 * The default {@code averagePeriod} is
 * {@value CandleThresholdSupport#DEFAULT_AVERAGE_PERIOD} bars.
 *
 * <p>
 * The direction of the pattern is owned by the first candle: for the bullish
 * Harami the first candle must be bearish and the second candle must be
 * bullish, the opposite color.
 *
 * <p>
 * This indicator does not evaluate trend or direction context: it reports the
 * two-candle morphology only. The conventional bullish reversal interpretation
 * after a downtrend is context this class does not own.
 *
 * @see <a href="http://www.investopedia.com/terms/b/bullishharami.asp">
 *      http://www.investopedia.com/terms/b/bullishharami.asp</a>
 */
public class BullishHaramiIndicator extends CandlePatternIndicator {

    private final int averagePeriod;

    /**
     * Constructor with the default period of 5 for the adaptive baselines.
     *
     * @param series the bar series
     */
    public BullishHaramiIndicator(BarSeries series) {
        this(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD);
    }

    /**
     * Constructor.
     *
     * @param series        the bar series
     * @param averagePeriod the number of preceding candles averaged into the
     *                      long/short body baselines (at least 1)
     * @since 0.24.2
     */
    public BullishHaramiIndicator(BarSeries series, int averagePeriod) {
        super(series, CandleThresholdSupport.forSeries(series, averagePeriod));
        this.averagePeriod = averagePeriod;
    }

    @Override
    int latestBaselineIndex(final int index) {
        return index - 1;
    }

    @Override
    protected Boolean calculate(int index) {
        if (index - 1 < getBarSeries().getBeginIndex()) {
            // Harami is a 2-candle pattern
            return false;
        }
        Bar prevBar = getBarSeries().getBar(index - 1);
        Bar currBar = getBarSeries().getBar(index);
        if (prevBar.isBearish() && thresholds.isLongBody(index - 1) && currBar.isBullish()
                && thresholds.isShortBody(index)) {
            Num prevBodyTop = prevBar.getOpenPrice().max(prevBar.getClosePrice());
            Num prevBodyBottom = prevBar.getOpenPrice().min(prevBar.getClosePrice());
            Num currBodyTop = currBar.getOpenPrice().max(currBar.getClosePrice());
            Num currBodyBottom = currBar.getOpenPrice().min(currBar.getClosePrice());
            // Signed zero is normalized in the inclusive clauses: DoubleNum
            // orders -0.0 below +0.0, so two numerically zero endpoints must
            // still count as equal (containment is inclusive).
            boolean topContained = currBodyTop.isLessThanOrEqual(prevBodyTop)
                    || (currBodyTop.isZero() && prevBodyTop.isZero());
            boolean bottomContained = currBodyBottom.isGreaterThanOrEqual(prevBodyBottom)
                    || (currBodyBottom.isZero() && prevBodyBottom.isZero());
            return topContained && bottomContained;
        }
        return false;
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod + 1;
    }
}
