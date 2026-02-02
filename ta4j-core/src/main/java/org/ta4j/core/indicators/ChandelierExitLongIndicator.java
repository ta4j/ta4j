/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * The Chandelier Exit (long) Indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chandelier_exit">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chandelier_exit</a>
 */
public class ChandelierExitLongIndicator extends CachedIndicator<Num> {

    private final HighestValueIndicator high;
    private final ATRIndicator atr;
    private final Num k;

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code barCount} = 22
     * <li>{@code k} = 3
     * </ul>
     *
     * @param series the bar series
     */
    public ChandelierExitLongIndicator(BarSeries series) {
        this(series, 22, 3);
    }

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame (usually 22)
     * @param k        the K multiplier for ATR (usually 3.0)
     */
    public ChandelierExitLongIndicator(BarSeries series, int barCount, double k) {
        super(series);
        this.high = new HighestValueIndicator(new HighPriceIndicator(series), barCount);
        this.atr = new ATRIndicator(series, barCount);
        this.k = getBarSeries().numFactory().numOf(k);
    }

    @Override
    protected Num calculate(int index) {
        return high.getValue(index).minus(atr.getValue(index).multipliedBy(k));
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
