/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.ichimoku;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Ichimoku clouds: Senkou Span B (Leading Span B) indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuSenkouSpanBIndicator extends CachedIndicator<Num> {

    /** Ichimoku avg line indicator. */
    private final IchimokuLineIndicator lineIndicator;

    /** Displacement on the chart (usually 26). */
    private final int offset;

    /**
     * Constructor with {@code barCount} = 52 and {@code offset} = 26.
     *
     * @param series the bar series
     */
    public IchimokuSenkouSpanBIndicator(BarSeries series) {
        this(series, 52, 26);
    }

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame (usually 52)
     */
    public IchimokuSenkouSpanBIndicator(BarSeries series, int barCount) {
        this(series, barCount, 26);
    }

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame (usually 52)
     * @param offset   displacement on the chart
     */
    public IchimokuSenkouSpanBIndicator(BarSeries series, int barCount, int offset) {
        super(series);
        this.lineIndicator = new IchimokuLineIndicator(series, barCount);
        this.offset = offset;
    }

    @Override
    protected Num calculate(int index) {
        int spanIndex = index - offset + 1;
        if (spanIndex >= getBarSeries().getBeginIndex()) {
            return lineIndicator.getValue(spanIndex);
        } else {
            return NaN.NaN;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return lineIndicator.getCountOfUnstableBars() + offset - 1;
    }
}
