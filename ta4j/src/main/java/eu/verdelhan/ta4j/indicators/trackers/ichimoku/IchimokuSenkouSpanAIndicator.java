/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.indicators.trackers.ichimoku;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

/**
 * Ichimoku clouds: Senkou Span A (Leading Span A) indicator
 * <p>
 * @see http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud
 */
public class IchimokuSenkouSpanAIndicator extends CachedIndicator<Decimal> {

    /** The Tenkan-sen indicator */
    private final IchimokuTenkanSenIndicator conversionLine;

    /** The Kijun-sen indicator */
    private final IchimokuKijunSenIndicator baseLine;

    /**
     * Constructor.
     * @param series the series
     */
    public IchimokuSenkouSpanAIndicator(TimeSeries series) {
        this(series, new IchimokuTenkanSenIndicator(series), new IchimokuKijunSenIndicator(series));
    }
    
    /**
     * Constructor.
     * @param series the series
     * @param timeFrameConversionLine the time frame for the conversion line (usually 9)
     * @param timeFrameBaseLine the time frame for the base line (usually 26)
     */
    public IchimokuSenkouSpanAIndicator(TimeSeries series, int timeFrameConversionLine, int timeFrameBaseLine) {
        this(series, new IchimokuTenkanSenIndicator(series, timeFrameConversionLine), new IchimokuKijunSenIndicator(series, timeFrameBaseLine));
    }
    
    /**
     * Constructor.
     * @param series the series
     * @param conversionLine the conversion line
     * @param baseLine the base line
     */
    public IchimokuSenkouSpanAIndicator(TimeSeries series, IchimokuTenkanSenIndicator conversionLine, IchimokuKijunSenIndicator baseLine) {
        super(series);
        this.conversionLine = conversionLine;
        this.baseLine = baseLine;
    }

    @Override
    protected Decimal calculate(int index) {
        return conversionLine.getValue(index).plus(baseLine.getValue(index)).dividedBy(Decimal.TWO);
    }
}
