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

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Ichimoku clouds: Senkou Span A (Leading Span A) indicator
 * * Ichimoku 云：Senkou Span A (Leading Span A) 指标
 *
 * Senkou Span A（先行线A）是一目均衡云指标中的一个部分，它用于显示将来的价格走势。Senkou Span A 是在一定时间段内的基准线（Kijun-sen）和转换线（Tenkan-sen）之间的中点，并且向前移动到将来一定时间段内。
 *
 * Senkou Span A 的计算方式是将基准线和转换线的平均值作为当前线，然后将这个值移动到未来一定时间段内。通常情况下，一目均衡云中的标准设置是将这个值移动到未来的 26 个周期。
 *
 * Senkou Span A 的计算公式如下：
 *
 * Senkou Span A = ( Tenkan-sen + Kijun-sen ) / 2
 *
 * 一目均衡云中的 Senkou Span A 线与其他指标一起使用，有助于确定未来的价格走势和支撑/阻力水平。当 Senkou Span A 处于价格图表的上方时，它可能作为一个未来的支撑水平；而当 Senkou Span A 处于价格图表的下方时，则可能作为一个未来的阻力水平。
 *
 * 总的来说，Senkou Span A 是一目均衡云中的一个重要指标，它用于显示未来的价格走势，并提供了一些支撑/阻力水平，有助于交易者做出更明智的交易决策。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud</a>
 */
public class IchimokuSenkouSpanAIndicator extends CachedIndicator<Num> {

    /** The Tenkan-sen indicator
     * Tenkan-sen 指标 */
    private final IchimokuTenkanSenIndicator conversionLine;

    /** The Kijun-sen indicator
     * Kijun-sen指标 */
    private final IchimokuKijunSenIndicator baseLine;

    // Cloud offset
    private final int offset;

    /**
     * Constructor.
     * 
     * @param series the series
     */
    public IchimokuSenkouSpanAIndicator(BarSeries series) {
        this(series, new IchimokuTenkanSenIndicator(series), new IchimokuKijunSenIndicator(series), 26);
    }

    /**
     * Constructor.
     * 
     * @param series                 the series
     * @param barCountConversionLine the time frame for the conversion line (usually 9)
     *                               转换线的时间范围（通常为 9）
     * @param barCountBaseLine       the time frame for the base line (usually 26)
     *                               基线的时间范围（通常为 26）
     */
    public IchimokuSenkouSpanAIndicator(BarSeries series, int barCountConversionLine, int barCountBaseLine) {
        this(series, new IchimokuTenkanSenIndicator(series, barCountConversionLine),
                new IchimokuKijunSenIndicator(series, barCountBaseLine), 26);
    }

    /**
     * Constructor.
     * 
     * @param series         the series
     * @param conversionLine the conversion line
     *                       转换线
     * @param baseLine       the base line
     *                       基线
     * @param offset         kumo cloud displacement (offset) forward in time
     *                       kumo 云位移（偏移）及时向前
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
        // 在 index=7 时，当 offset=5 时我们需要 index=3
        int spanIndex = index - offset + 1;
        if (spanIndex >= getBarSeries().getBeginIndex()) {
            return conversionLine.getValue(spanIndex).plus(baseLine.getValue(spanIndex)).dividedBy(numOf(2));
        } else {
            return NaN.NaN;
        }
    }
}
