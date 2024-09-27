/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
    public int getUnstableBars() {
        return 0;
    }
}
