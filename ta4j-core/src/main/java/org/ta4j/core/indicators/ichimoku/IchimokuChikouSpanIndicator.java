/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.ichimoku;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Ichimoku clouds: Chikou Span indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuChikouSpanIndicator extends CachedIndicator<Num> {

    /** The close price. */
    private final ClosePriceIndicator closePriceIndicator;

    /** The time delay. */
    private final int timeDelay;

    /**
     * Constructor with {@code barCount} = 26.
     *
     * @param series the bar series
     */
    public IchimokuChikouSpanIndicator(BarSeries series) {
        this(series, 26);
    }

    /**
     * Constructor.
     *
     * @param series    the bar series
     * @param timeDelay the time delay (usually 26)
     */
    public IchimokuChikouSpanIndicator(BarSeries series, int timeDelay) {
        super(series);
        this.closePriceIndicator = new ClosePriceIndicator(series);
        this.timeDelay = timeDelay;
    }

    @Override
    protected Num calculate(int index) {
        int spanIndex = index + timeDelay;
        if (spanIndex <= getBarSeries().getEndIndex()) {
            return closePriceIndicator.getValue(spanIndex);
        } else {
            return NaN.NaN;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
