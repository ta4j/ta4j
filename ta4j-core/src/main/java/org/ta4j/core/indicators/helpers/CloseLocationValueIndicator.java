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
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Close Location Value (CLV) indicator.
 * 关闭位置值 (CLV) 指标。
 *
 * Close Location Value (CLV) 指标，也称为相对位置指标（Relative Location Value），是一种用于技术分析的指标，用于衡量收盘价在给定周期内的位置相对于价格范围的位置。它可以帮助分析价格相对于价格范围的位置，并据此判断市场的买卖力量以及价格可能的走势。
 *
 * ### 计算方法
 * CLV 指标的计算步骤如下：
 * 1. 计算当日价格范围（即当日最高价和最低价之间的价格差）：
 *    Price Range = High−Low
 * 2. 计算当日收盘价相对于价格范围的位置：
 *    CLV = ( Close - Low) / (High - Low) * 100
 *    其中，Close 表示当日收盘价，High 表示当日最高价，Low 表示当日最低价。
 *
 * ### 意义
 * CLV 指标的主要意义在于：
 * - 衡量收盘价在价格范围内的位置，提供了关于市场买卖力量的信息。
 * - 可以用于确认价格趋势和判断价格反转的可能性。
 * - 可以作为其他技术指标的辅助，用于生成交易信号或过滤信号。
 *
 * ### 应用
 * CLV 指标的应用包括但不限于以下方面：
 * - **确认趋势**：当 CLV 值处于较高水平时，表示收盘价接近当日价格范围的高点，可能表明市场处于上升趋势；反之，当 CLV 值处于较低水平时，表示收盘价接近当日价格范围的低点，可能表明市场处于下降趋势。
 * - **信号生成**：当 CLV 值出现明显的变化或转折时，可能产生买入或卖出信号，例如，当 CLV 值从低位反转向上升至高位时，可能产生买入信号。
 * - **过滤器**：可作为其他指标的过滤器，用于排除某些无效的信号，提高交易策略的准确性。
 *
 * ### 注意事项
 * - CLV 指标应该结合其他技术指标和价格分析方法一起使用，以避免产生误导性的信号。
 * - 建议在具体市场和交易策略中进行测试和优化，以确定最佳的 CLV 参数。
 * - 交易者应根据具体情况和市场条件，灵活调整 CLV 指标的应用和解读。
 *
 * ### 总结
 * Close Location Value (CLV) 指标是一种用于衡量收盘价在价格范围内的位置相对于价格范围的指标。它可以提供有关市场买卖力量和价格走势的信息，用于确认趋势、生成交易信号和过滤信号。在使用时，应结合其他技术指标和价格分析方法，并根据具体情况进行调整和确认。
 *
 * @see <a href="http://www.investopedia.com/terms/c/close_location_value.asp">
 *      http://www.investopedia.com/terms/c/close_location_value.asp</a>
 */
public class CloseLocationValueIndicator extends CachedIndicator<Num> {

    private final Num zero = numOf(0);

    public CloseLocationValueIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        final Bar bar = getBarSeries().getBar(index);
        final Num low = bar.getLowPrice();
        final Num high = bar.getHighPrice();
        final Num close = bar.getClosePrice();

        final Num diffHighLow = high.minus(low);

        return (diffHighLow.isNaN() || diffHighLow.isZero()) ? zero
                : ((close.minus(low)).minus(high.minus(close))).dividedBy(diffHighLow);
    }
}
