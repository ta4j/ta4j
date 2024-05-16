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
package org.ta4j.core.indicators.bollinger;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

/**
 * %B indicator.
 * %B 指标。
 *
 * %B 指标（百分比B指标）是一个技术分析工具，用于衡量当前价格相对于布林带的位置。它由约翰·布林格（John Bollinger）创建，作为布林带系统的补充工具。
 *
 * ### 计算方法
 * %B 指标的计算公式如下：
 * %B = {当前价格} - {下轨线}  /  {{上轨线} - {下轨线}}
 *
 * 其中：
 * - **上轨线** = 移动平均线 + (标准差的倍数 × 标准差)
 * - **下轨线** = 移动平均线 - (标准差的倍数 × 标准差)
 *
 * 通常，布林带的标准设置是20期的简单移动平均线（SMA），标准差的倍数为2。
 *
 * ### %B 的解读
 * %B 指标的值通常在 0 到 1 之间波动，但在极端情况下，它可以超出这个范围：
 * - **%B = 1**：当前价格位于布林带的上轨线上。
 * - **%B = 0**：当前价格位于布林带的下轨线上。
 * - **%B > 1**：当前价格高于布林带的上轨线，表示超买状态。
 * - **%B < 0**：当前价格低于布林带的下轨线，表示超卖状态。
 * - **%B = 0.5**：当前价格位于布林带的中线（通常是移动平均线）。
 *
 * ### 使用策略
 * 1. **识别超买和超卖状态**：
 *     - 当 %B 大于 1 时，市场可能处于超买状态，价格可能会回调。
 *     - 当 %B 小于 0 时，市场可能处于超卖状态，价格可能会反弹。
 *
 * 2. **趋势确认**：
 *     - 在强上升趋势中，%B 往往在 0.8 和 1 之间波动，甚至超过 1。
 *     - 在强下降趋势中，%B 往往在 0 和 0.2 之间波动，甚至低于 0。
 *
 * 3. **交易信号**：
 *     - **买入信号**：当 %B 从低于 0 回升到 0 以上，可能是买入信号，尤其是在布林带收缩后。
 *     - **卖出信号**：当 %B 从高于 1 回落到 1 以下，可能是卖出信号，尤其是在布林带扩张后。
 *
 * 4. **结合其他指标**：
 *     - %B 指标可以与其他技术指标（如相对强弱指数 RSI 或移动平均线）结合使用，以确认交易信号的有效性。
 *
 * ### 示例
 * - **超买状态**：如果 %B 大于 1，交易者可能会关注价格是否会回调，并考虑卖出或获利了结。
 * - **超卖状态**：如果 %B 小于 0，交易者可能会关注价格是否会反弹，并考虑买入或建立多头头寸。
 *
 * ### 注意事项
 * - **滞后性**：和其他基于移动平均线的指标一样，%B 可能会滞后于市场实际价格的变化。
 * - **结合其他分析方法**：单独使用 %B 可能会产生误导信号，通常需要结合其他技术指标和市场分析方法进行综合判断。
 * - **市场环境**：不同市场和不同时间框架下，%B 的表现可能有所不同，需要根据具体情况调整使用策略。
 *
 * ### 总结
 * %B 指标通过衡量当前价格相对于布林带的位置，帮助交易者识别超买和超卖状态，确认趋势，并提供交易信号。它是一个有效的工具，尤其在结合其他技术分析工具使用时，可以为交易决策提供有价值的参考。
 * 
 * @see <a
 *      href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_perce>
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_perce</a>
 */
public class PercentBIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;

    private final BollingerBandsUpperIndicator bbu;

    private final BollingerBandsLowerIndicator bbl;

    /**
     * Constructor.
     * 构造器
     * 
     * @param indicator an indicator (usually close price)
     *                  一个指标（通常是收盘价）
     * @param barCount  the time frame
     *                  时间框架
     * @param k         the K multiplier (usually 2.0)
     *                  K 乘数（通常为 2.0）
     */
    public PercentBIndicator(Indicator<Num> indicator, int barCount, double k) {
        super(indicator);
        this.indicator = indicator;
        BollingerBandsMiddleIndicator bbm = new BollingerBandsMiddleIndicator(new SMAIndicator(indicator, barCount));
        StandardDeviationIndicator sd = new StandardDeviationIndicator(indicator, barCount);
        this.bbu = new BollingerBandsUpperIndicator(bbm, sd, numOf(k));
        this.bbl = new BollingerBandsLowerIndicator(bbm, sd, numOf(k));
    }

    @Override
    protected Num calculate(int index) {
        Num value = indicator.getValue(index);
        Num upValue = bbu.getValue(index);
        Num lowValue = bbl.getValue(index);
        return value.minus(lowValue).dividedBy(upValue.minus(lowValue));
    }
}
