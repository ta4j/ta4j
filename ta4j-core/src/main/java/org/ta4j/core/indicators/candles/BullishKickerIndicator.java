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
 * Bullish kicker candle indicator.
 *
 * <p>
 * Detected at index {@code i} when the two-candle sequence ending at {@code i}
 * satisfies
 *
 * <pre>
 * first.isBearish() &amp;&amp; second.isBullish() &amp;&amp; isMarubozu(i - 1) &amp;&amp; isMarubozu(i) &amp;&amp; bodyBottom(i) &gt; bodyTop(i - 1)
 * </pre>
 *
 * where {@code bodyTop = max(open, close)} and
 * {@code bodyBottom = min(open, close)}, and
 * {@code isMarubozu(k) = isLongBody(k) &amp;&amp; isShortShadow(k, upper)
 * &amp;&amp; isShortShadow(k, lower)}. {@code isLongBody} requires a body
 * strictly greater than the {@code averagePeriod}-bar prior average body
 * (factor 1.0) and {@code isShortShadow} requires a shadow no larger than
 * {@code 0.1} of the prior average range, so both candles are marubozu (long
 * body, negligible shadows). The real-body gap is strict: the second body must
 * open strictly above the top of the first body.
 *
 * <p>
 * The polarity of the pattern is owned by the second candle: for the bullish
 * kicker the second candle is bullish and the first candle is bearish, both
 * opposite and gap-separated.
 *
 * <p>
 * This indicator does not evaluate trend or direction context: it reports the
 * two-candle morphology only. The conventional bullish reversal interpretation
 * is context this class does not own.
 *
 * @see <a href="https://www.investopedia.com/terms/k/kickerpattern.asp">
 *      https://www.investopedia.com/terms/k/kickerpattern.asp</a>
 * @since 0.22.2
 */
public class BullishKickerIndicator extends CandlePatternIndicator {

    private final int averagePeriod;
    private final transient Indicator<Num> upperShadow;
    private final transient Indicator<Num> lowerShadow;

    /**
     * Constructor with the default period of 5 for the adaptive baselines.
     *
     * @param series the bar series
     */
    public BullishKickerIndicator(final BarSeries series) {
        this(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD);
    }

    /**
     * Constructor.
     *
     * @param series        the bar series
     * @param averagePeriod the number of preceding candles averaged into the
     *                      long-body and short-shadow baselines (at least 1)
     * @since 0.24.2
     */
    public BullishKickerIndicator(final BarSeries series, final int averagePeriod) {
        super(series, CandleThresholdSupport.forSeries(series, averagePeriod));
        this.averagePeriod = averagePeriod;
        this.upperShadow = thresholds.upperShadow();
        this.lowerShadow = thresholds.lowerShadow();
    }

    @Override
    int latestBaselineIndex(final int index) {
        return index - 1;
    }

    @Override
    protected Boolean calculate(int index) {
        if (index - 1 < getBarSeries().getBeginIndex()) {
            // Kicker is a 2-candle pattern
            return false;
        }
        Bar firstBar = getBarSeries().getBar(index - 1);
        Bar secondBar = getBarSeries().getBar(index);
        // Optional OHLC endpoints carry no body or shadow geometry; the body
        // and shadow indicators would dereference null prices before the
        // non-finite guard.
        if (firstBar.getOpenPrice() == null || firstBar.getHighPrice() == null || firstBar.getLowPrice() == null
                || firstBar.getClosePrice() == null || secondBar.getOpenPrice() == null
                || secondBar.getHighPrice() == null || secondBar.getLowPrice() == null
                || secondBar.getClosePrice() == null) {
            return false;
        }
        // Non-finite shadow measurements are undefined; the negated
        // short-shadow checks would otherwise treat them as negligible.
        if (!Num.isFinite(upperShadow.getValue(index - 1)) || !Num.isFinite(lowerShadow.getValue(index - 1))
                || !Num.isFinite(upperShadow.getValue(index)) || !Num.isFinite(lowerShadow.getValue(index))) {
            return false;
        }
        boolean firstMarubozu = firstBar.isBearish() && thresholds.isLongBody(index - 1)
                && thresholds.isShortShadow(index - 1, upperShadow) && thresholds.isShortShadow(index - 1, lowerShadow);
        boolean secondMarubozu = secondBar.isBullish() && thresholds.isLongBody(index)
                && thresholds.isShortShadow(index, upperShadow) && thresholds.isShortShadow(index, lowerShadow);
        if (firstMarubozu && secondMarubozu) {
            Num firstBodyTop = firstBar.getOpenPrice().max(firstBar.getClosePrice());
            Num secondBodyBottom = secondBar.getOpenPrice().min(secondBar.getClosePrice());
            // The second body must open strictly above the first body. Signed
            // zero is normalized first: DoubleNum orders -0.0 below +0.0, so two
            // numerically zero endpoints would otherwise register a phantom gap.
            return !(secondBodyBottom.isZero() && firstBodyTop.isZero())
                    && secondBodyBottom.isGreaterThan(firstBodyTop);
        }
        return false;
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod + 1;
    }
}
