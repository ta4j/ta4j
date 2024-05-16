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

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.Num;

/**
 * Doji indicator.
 * 十字线 /十字星 /无实体线 指标。
 * A candle/bar is considered Doji if its body height is lower than the average multiplied by a factor.
 * 如果烛台的体高低于平均值乘以系数，则认为烛台为十字星。
 *
 * Doji 是一种重要的蜡烛图形态，表示市场的犹豫不决和潜在的反转信号。Doji 蜡烛的开盘价和收盘价几乎相同，形成一个很小的实体或没有实体。Doji 可以出现在任何趋势中，并且它的出现可能预示着市场趋势的改变或现有趋势的延续。
 *
 * ### 特征
 * - **开盘价和收盘价几乎相同**：Doji 蜡烛的开盘价和收盘价非常接近或相同，因此其实体很小或几乎不存在。
 * - **上下影线的长度**：Doji 蜡烛可能有长短不一的上下影线，影线的长度取决于当天的价格波动。
 *
 * ### Doji 类型
 * 1. **标准 Doji**：开盘价和收盘价几乎相同，上下影线长度相似。
 * 2. **长脚 Doji（Long-legged Doji）**：上下影线较长，表示市场在高价和低价之间剧烈波动，但最终未能决定方向。
 * 3. **墓碑 Doji（Gravestone Doji）**：开盘价和收盘价接近于当日最低价，形成较长的上影线，预示着市场可能由多转空。
 * 4. **蜻蜓 Doji（Dragonfly Doji）**：开盘价和收盘价接近于当日最高价，形成较长的下影线，预示着市场可能由空转多。
 *
 * ### 识别方法
 * 1. **观察实体**：Doji 蜡烛的开盘价和收盘价几乎相同或相同，因此实体很小或几乎不存在。
 * 2. **上下影线**：影线长度取决于当天的价格波动。
 *
 * ### 交易策略
 * 1. **趋势反转**：
 *     - **看涨反转**：在下降趋势的底部出现 Doji，可能预示着卖方力量减弱，买方力量增强，是一个潜在的买入信号。
 *     - **看跌反转**：在上升趋势的顶部出现 Doji，可能预示着买方力量减弱，卖方力量增强，是一个潜在的卖出信号。
 *
 * 2. **确认信号**：单独一个 Doji 并不足以确认趋势的反转，需要结合其他技术指标或形态来确认信号。例如：
 *     - **看涨信号**：在下降趋势末端出现 Doji 后，紧接着出现一根较大的阳线，可以确认看涨反转。
 *     - **看跌信号**：在上升趋势末端出现 Doji 后，紧接着出现一根较大的阴线，可以确认看跌反转。
 *
 * 3. **支撑和阻力**：Doji 形态常出现在重要的支撑或阻力位，结合这些水平可以提高信号的准确性。
 *
 * ### 示例
 * 假设市场处于上升趋势中：
 * - **第一天**：形成多根阳线，显示市场强劲的买方力量。
 * - **第二天**：形成一个 Doji 蜡烛，开盘价和收盘价几乎相同，显示市场犹豫不决。
 *
 * 这时，可以观察后续蜡烛，如果出现一根较大的阴线确认看跌信号，可以考虑卖出，止损位设在 Doji 形态的最高点上方，目标价位设在前一个支撑位附近。
 *
 * ### 注意事项
 * - **趋势确认**：Doji 形态在不同的趋势中意义不同，需结合前期趋势来判断其意义。
 * - **结合其他指标**：单独使用 Doji 可能会产生误导信号，建议结合其他技术指标（如相对强弱指数 RSI、移动平均线 MACD）进行综合分析。
 * - **市场环境**：不同市场和时间框架下，Doji 形态的表现可能有所不同，需根据具体情况调整策略。
 *
 * ### 总结
 * Doji 是一种重要的反转形态，通过开盘价和收盘价几乎相同的特征来识别。结合其他技术指标和市场分析方法，可以提高交易决策的准确性。在实际应用中，交易者应根据具体市场环境和自身风险承受能力进行综合判断。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#doji">
 *      http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#doji</a>
 */
public class DojiIndicator extends CachedIndicator<Boolean> {

    /**
     * Body height
     * 身高
     */
    private final Indicator<Num> bodyHeightInd;
    /**
     * Average body height
     * 平均身高
     */
    private final SMAIndicator averageBodyHeightInd;

    private final Num factor;

    /**
     * Constructor.
     *
     * @param series     the bar series
     *                  bar 系列
     * @param barCount   the number of bars used to calculate the average body  height
     *                   用于计算平均身高的条数
     * @param bodyFactor the factor used when checking if a candle is Doji
     *                   检查蜡烛是否为十字星时使用的因素
     */
    public DojiIndicator(BarSeries series, int barCount, double bodyFactor) {
        super(series);
        bodyHeightInd = TransformIndicator.abs(new RealBodyIndicator(series));
        averageBodyHeightInd = new SMAIndicator(bodyHeightInd, barCount);
        factor = numOf(bodyFactor);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < 1) {
            return bodyHeightInd.getValue(index).isZero();
        }

        Num averageBodyHeight = averageBodyHeightInd.getValue(index - 1);
        Num currentBodyHeight = bodyHeightInd.getValue(index);

        return currentBodyHeight.isLessThan(averageBodyHeight.multipliedBy(factor));
    }
}
