/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Bearish engulfing pattern indicator.
 *
 * <p>
 * Detected at index {@code i} when the two-candle sequence ending at {@code i}
 * satisfies
 *
 * <pre>
 * prev.isBullish() &amp;&amp; curr.isBearish() &amp;&amp; bodyTop(curr) &gt;= bodyTop(prev) &amp;&amp; bodyBottom(curr) &lt;= bodyBottom(prev)
 *         &amp;&amp; (bodyTop(curr) &gt; bodyTop(prev) || bodyBottom(curr) &lt; bodyBottom(prev))
 * </pre>
 *
 * where {@code bodyTop = max(open, close)} and
 * {@code bodyBottom = min(open, close)}. The current (bearish) body must
 * therefore contain the previous (bullish) body. Shared endpoints count (the
 * {@code >=}/{@code <=} comparisons are inclusive), while two exactly-identical
 * bodies do not match because at least one endpoint must be strictly exceeded.
 *
 * <p>
 * This indicator does not evaluate trend or direction context: it reports the
 * two-candle morphology only. The conventional bearish reversal interpretation
 * after an uptrend is context this class does not own.
 *
 * @see <a href="http://www.investopedia.com/terms/b/bearishengulfingp.asp">
 *      http://www.investopedia.com/terms/b/bearishengulfingp.asp</a>
 */
public class BearishEngulfingIndicator extends CachedIndicator<Boolean> {

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public BearishEngulfingIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index - 1 < getBarSeries().getBeginIndex()) {
            // Engulfing is a 2-candle pattern
            return false;
        }
        Bar prevBar = getBarSeries().getBar(index - 1);
        Bar currBar = getBarSeries().getBar(index);
        if (prevBar.isBullish() && currBar.isBearish() && Num.isFinite(prevBar.getOpenPrice())
                && Num.isFinite(prevBar.getClosePrice()) && Num.isFinite(currBar.getOpenPrice())
                && Num.isFinite(currBar.getClosePrice())) {
            Num prevBodyTop = prevBar.getOpenPrice().max(prevBar.getClosePrice());
            Num prevBodyBottom = prevBar.getOpenPrice().min(prevBar.getClosePrice());
            Num currBodyTop = currBar.getOpenPrice().max(currBar.getClosePrice());
            Num currBodyBottom = currBar.getOpenPrice().min(currBar.getClosePrice());
            // Signed zero is normalized in the strict clause: DoubleNum orders
            // -0.0 below +0.0, so two numerically zero endpoints must not count
            // as a strict difference (the Javadoc excludes identical bodies).
            boolean topDiffers = !(currBodyTop.isZero() && prevBodyTop.isZero())
                    && currBodyTop.isGreaterThan(prevBodyTop);
            boolean bottomDiffers = !(currBodyBottom.isZero() && prevBodyBottom.isZero())
                    && currBodyBottom.isLessThan(prevBodyBottom);
            // Signed zero is normalized in the inclusive clauses too: DoubleNum
            // orders -0.0 below +0.0, so two numerically zero endpoints must
            // still count as containment (the Javadoc allows shared endpoints).
            boolean topContains = currBodyTop.isGreaterThanOrEqual(prevBodyTop)
                    || (currBodyTop.isZero() && prevBodyTop.isZero());
            boolean bottomContains = currBodyBottom.isLessThanOrEqual(prevBodyBottom)
                    || (currBodyBottom.isZero() && prevBodyBottom.isZero());
            return topContains && bottomContains && (topDiffers || bottomDiffers);

        }
        return false;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 1;
    }
}
