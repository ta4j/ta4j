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
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Real (candle) body height indicator.
 * 真实（蜡烛）身高指示器。
 *
 * Provides the (relative) difference between the open price and the close price of a bar. I.e.: close price - open price
 * 提供柱线的开盘价和收盘价之间的（相对）差值。即：收盘价 - 开盘价
 *
 * Real body height indicator（实体高度指标）是一种用于分析蜡烛图的技术指标，用于衡量市场在某一时间段内的价格波动的幅度。实体高度指标主要关注蜡烛图的实体部分，即开盘价和收盘价之间的距离。
 *
 * ### 计算方法
 * 实体高度通常是以蜡烛图的实体部分的高度来衡量的。计算方法如下：
 * 1. 找到蜡烛图的开盘价和收盘价。
 * 2. 计算这两个价格之间的距离，即为实体的高度。
 *
 * ### 意义
 * 实体高度指标可以提供有关市场价格波动幅度的信息：
 * - **较长的实体**：表示市场价格波动幅度较大，市场情绪可能比较激烈。
 * - **较短的实体**：表示市场价格波动幅度较小，市场情绪可能比较平静。
 *
 * ### 应用
 * 1. **趋势确认**：在上升趋势中，较长的实体可能表明买方力量较强，有助于确认趋势的延续；在下降趋势中，较长的实体可能表明卖方力量较强，有助于确认趋势的延续。
 * 2. **反转信号**：特别是在出现长上影线或长下影线的情况下，较长的实体可能预示着价格反转的可能性。
 * 3. **支撑和阻力**：较长的实体通常也与支撑和阻力位相关联，可以帮助确定这些价格水平的重要性。
 *
 * ### 注意事项
 * - 实体高度指标通常结合其他技术指标和图表模式一起使用，以提高分析的准确性。
 * - 单独使用实体高度指标可能会产生假信号，因此需要谨慎对待，并结合其他技术分析方法进行确认。
 *
 * ### 示例
 * 假设某个交易日的蜡烛图有较长的实体，表示开盘价和收盘价之间的价格波动幅度较大，市场情绪可能比较激烈。如果该交易日的实体高度显著超过了前几个交易日的实体高度，这可能表明市场情绪的变化，需要进一步关注价格走势。
 *
 * ### 总结
 * 实体高度指标是一种用于分析蜡烛图的技术指标，通过衡量蜡烛图实体部分的高度来评估市场的价格波动幅度和市场情绪。较长的实体可能预示着市场的波动较大，较短的实体可能表明市场的波动较小，但需要谨慎对待，并结合其他技术指标和图表模式进行分析和确认。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation">
 *      http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation</a>
 */
public class RealBodyIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series a bar series
     */
    public RealBodyIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Bar t = getBarSeries().getBar(index);
        return t.getClosePrice().minus(t.getOpenPrice());
    }
}
