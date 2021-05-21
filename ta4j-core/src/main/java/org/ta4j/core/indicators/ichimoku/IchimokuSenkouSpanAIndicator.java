/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import static org.ta4j.core.indicators.helpers.CombineIndicator.plus;
import static org.ta4j.core.indicators.helpers.TransformIndicator.divide;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Ichimoku clouds: Senkou Span A (Leading Span A) indicator
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuSenkouSpanAIndicator extends CachedIndicator<Num> {

    /** The actual indicator which contains the calculation */
    private final TransformIndicator senkouSpanAFutureCalculator;

    /**
     * Constructor.
     * 
     * @param series the series
     */
    public IchimokuSenkouSpanAIndicator(BarSeries series) {
        this(new IchimokuTenkanSenIndicator(series), new IchimokuKijunSenIndicator(series));
    }

    /**
     * Constructor. This indicator returns the values in dependency to the current
     * time 'index'. This means, it is the 'future' cloud indicator (when printed)
     * The values are calculated for the current bar, but the values are printed
     * into the future
     *
     * To create an indicator which returns the values of the 'current' cloud (when
     * printed), use new PreviousValueIndicator(ichimokuSenkouSpanAIndicator,
     * senkunSpanBarCount/2))
     *
     * To create an indicator which contains the values of the 'past' cloud (when
     * printed), use new PreviousValueIndicator(ichimokuSenkouSpanAIndicator,
     * senkunSpanBarCount))
     * 
     * @param series                 the series
     * @param barCountConversionLine the time frame for the conversion line (usually
     *                               9)
     * @param barCountBaseLine       the time frame for the base line (usually 26)
     */
    public IchimokuSenkouSpanAIndicator(BarSeries series, int barCountConversionLine, int barCountBaseLine) {
        this(new IchimokuTenkanSenIndicator(series, barCountConversionLine),
                new IchimokuKijunSenIndicator(series, barCountBaseLine));
    }

    /**
     * Constructor.
     *
     * @param conversionLine the conversion line
     * @param baseLine       the base line
     */
    public IchimokuSenkouSpanAIndicator(IchimokuTenkanSenIndicator conversionLine, IchimokuKijunSenIndicator baseLine) {
        super(conversionLine.getBarSeries());
        senkouSpanAFutureCalculator = divide(plus(conversionLine, baseLine), 2);
    }

    @Override
    protected Num calculate(int index) {
        return senkouSpanAFutureCalculator.getValue(index);
    }
}
