/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * The Moving volume weighted average price (MVWAP) Indicator.
 *
 * @see <a href=
 *      "http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp">
 *      http://www.investopedia.com/articles/trading/11/trading-with-vwap-mvwap.asp</a>
 */
public class MVWAPIndicator extends CachedIndicator<Num> {

    private final VWAPIndicator vwap;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param vwap     the vwap
     * @param barCount the time frame
     */
    public MVWAPIndicator(VWAPIndicator vwap, int barCount) {
        super(vwap);
        this.vwap = vwap;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        if (index < beginIndex + getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        Num sum = getBarSeries().numFactory().zero();
        int startIndex = index - barCount + 1;
        for (int i = startIndex; i <= index; i++) {
            Num value = vwap.getValue(i);
            if (isInvalid(value)) {
                return NaN.NaN;
            }
            sum = sum.plus(value);
        }
        return sum.dividedBy(getBarSeries().numFactory().numOf(barCount));
    }

    @Override
    public int getCountOfUnstableBars() {
        return vwap.getCountOfUnstableBars() + barCount - 1;
    }

    private static boolean isInvalid(Num value) {
        if (Num.isNaNOrNull(value)) {
            return true;
        }
        return Double.isNaN(value.doubleValue());
    }
}
