/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.ichimoku;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Ichimoku clouds: Senkou Span A (Leading Span A) indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuSenkouSpanAIndicator extends CachedIndicator<Num> {

    /** The Tenkan-sen indicator. */
    private final IchimokuTenkanSenIndicator conversionLine;

    /** The Kijun-sen indicator. */
    private final IchimokuKijunSenIndicator baseLine;

    /** The cloud offset. */
    private final int offset;

    /**
     * Constructor with {@code offset} = 26.
     *
     * @param series the bar series
     */
    public IchimokuSenkouSpanAIndicator(BarSeries series) {
        this(series, new IchimokuTenkanSenIndicator(series), new IchimokuKijunSenIndicator(series), 26);
    }

    /**
     * Constructor.
     *
     * @param series                 the bar series
     * @param barCountConversionLine the time frame for the conversion line (usually
     *                               9)
     * @param barCountBaseLine       the time frame for the base line (usually 26)
     */
    public IchimokuSenkouSpanAIndicator(BarSeries series, int barCountConversionLine, int barCountBaseLine) {
        this(series, new IchimokuTenkanSenIndicator(series, barCountConversionLine),
                new IchimokuKijunSenIndicator(series, barCountBaseLine), 26);
    }

    /**
     * Constructor.
     *
     * @param series         the bar series
     * @param conversionLine the conversion line
     * @param baseLine       the base line
     * @param offset         kumo cloud displacement (offset) forward in time
     */
    public IchimokuSenkouSpanAIndicator(BarSeries series, IchimokuTenkanSenIndicator conversionLine,
            IchimokuKijunSenIndicator baseLine, int offset) {
        super(series);
        this.conversionLine = conversionLine;
        this.baseLine = baseLine;
        this.offset = offset;
    }

    @Override
    protected Num calculate(int index) {
        // at index=7 we need index=3 when offset=5
        int spanIndex = index - offset + 1;
        if (spanIndex >= getBarSeries().getBeginIndex()) {
            return conversionLine.getValue(spanIndex)
                    .plus(baseLine.getValue(spanIndex))
                    .dividedBy(getBarSeries().numFactory().two());
        } else {
            return NaN.NaN;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        int baseUnstableBars = Math.max(conversionLine.getCountOfUnstableBars(), baseLine.getCountOfUnstableBars());
        return baseUnstableBars + offset - 1;
    }
}
