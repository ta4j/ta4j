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
 * Lower shadow height indicator.
 * 下阴影高度指示器。
 *
 * Provides the (absolute) difference between the low price and the lowest price of the candle body. I.e.: low price - min(open price, close price)
 * 提供蜡烛体的最低价和最低价之间的（绝对）差值。即：低价 - 最小（开盘价，收盘价）
 *
 *
 * Lower shadow height indicator（下影线高度指标）是一种用于分析蜡烛图的技术指标，主要用于评估市场买卖力量的强弱和市场情绪的变化。下影线是蜡烛图中实体下方的线段，表示了市场在某一时间段内的最低价和收盘价之间的距离。
 *
 * ### 计算方法
 * 下影线的高度通常是以实体的最低点和下影线的最低点之间的距离来衡量的。计算方法如下：
 * 1. 找到蜡烛图实体的最低价（低点）。
 * 2. 找到下影线的最低点（通常是实体下方的最低点）。
 * 3. 计算这两点之间的距离，即为下影线的高度。
 *
 * ### 意义
 * 下影线的高度可以提供关于市场的买卖压力以及买卖力量的平衡情况的信息：
 * - **较长的下影线**：表示市场在最低价附近遇到了买盘支撑，买方力量较强，可能预示着价格的反弹或趋势的转变。
 * - **较短的下影线**：表示市场在最低价附近买卖力量较为均衡，市场情绪可能比较稳定。
 *
 * ### 应用
 * 1. **趋势确认**：在上升趋势中，较长的下影线可能预示着买方力量仍然较强，有助于确认趋势的延续。
 * 2. **反转信号**：在下降趋势中，较长的下影线可能预示着卖方力量减弱，市场情绪可能转为买方，是一种潜在的反转信号。
 * 3. **支撑位分析**：下影线的高度也可以帮助确定支撑位的强度，较长的下影线可能意味着该价格水平有较多的买盘支撑。
 *
 * ### 注意事项
 * - 下影线高度指标通常结合其他技术指标和图表模式一起使用，以提高分析的准确性。
 * - 单独使用下影线高度指标可能会产生假信号，因此需要谨慎对待，并结合其他技术分析方法进行确认。
 *
 * ### 示例
 * 假设某个交易日的蜡烛图有较长的下影线，表明市场在最低价附近遇到了买盘支撑，这可能会导致价格的反弹。如果该交易日的下影线高度显著超过了前几个交易日的下影线高度，这可能表明买方力量在增强，进一步支持了价格反弹的可能性。
 *
 * ### 总结
 * 下影线高度指标是一种用于分析蜡烛图的技术指标，通过衡量下影线的高度来评估市场的买卖力量和市场情绪的变化。较长的下影线可能预示着市场的反弹或趋势的转变，但需要谨慎对待，并结合其他技术指标和图表模式进行分析和确认。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation">
 *      http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#formation</a>
 */
public class LowerShadowIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     *
     * @param series a bar series
     */
    public LowerShadowIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        Bar t = getBarSeries().getBar(index);
        final Num openPrice = t.getOpenPrice();
        final Num closePrice = t.getClosePrice();
        if (closePrice.isGreaterThan(openPrice)) {
            // Bullish
            //看涨
            return openPrice.minus(t.getLowPrice());
        } else {
            // Bearish
            //看跌
            return closePrice.minus(t.getLowPrice());
        }
    }
}
