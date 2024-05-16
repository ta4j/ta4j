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

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Amount indicator.
 * 总量指标
 *
 * "Amount" indicator通常指的是成交量指标，它是用于技术分析的重要指标之一。成交量指标衡量的是某一资产在特定时间段内的交易量，通常以柱状图的形式显示在价格图表的下方。成交量的增加或减少可以提供有关市场参与者情绪和趋势强度的重要信息。
 *
 * ### 意义
 * 成交量指标在技术分析中有多种用途：
 * 1. **确认趋势**：价格和成交量通常呈正相关关系，即价格上涨时成交量增加，价格下跌时成交量减少。因此，成交量的增加或减少可以用来确认价格趋势的持续性。
 * 2. **趋势转变**：成交量的急剧增加或减少可能预示着价格趋势的转变，尤其是当价格出现突破时，伴随着高成交量的突破更有可能是真实的突破信号。
 * 3. **价格波动**：价格波动伴随着高成交量可能表明市场情绪的激烈变化，可能预示着市场的波动性增加。
 * 4. **支撑和阻力**：成交量的变化可以帮助识别支撑和阻力水平，例如，在价格回调到支撑位时，如果成交量明显减少，则可能表明该支撑位具有强大的支撑力量。
 *
 * ### 应用
 * 成交量指标的应用取决于具体的交易策略和市场情况：
 * - **结合价格分析**：成交量指标通常与价格分析一起使用，例如，确认价格趋势的持续性、验证价格突破的真实性等。
 * - **交易信号**：某些交易策略可能直接使用成交量指标生成交易信号，例如，价格与成交量的背离等。
 * - **配合其他指标**：成交量指标通常与其他技术指标结合使用，如移动平均线、相对强弱指标等，以提高交易决策的准确性。
 *
 * ### 注意事项
 * - 成交量指标应该结合其他技术指标和价格分析一起使用，以避免产生误导性的信号。
 * - 在不同的市场和时间段内，成交量的参考标准可能会有所不同，交易者应该根据具体情况进行调整。
 * - 成交量指标的数据来源和计算方法可能因交易平台或数据提供商而异，需要在使用前进行确认。
 *
 * ### 总结
 * 成交量指标是技术分析中的重要指标之一，用于衡量资产在特定时间段内的交易量。它可以提供有关市场参与者情绪、趋势强度和价格波动的重要信息，是交易决策中不可或缺的一部分。成交量指标应该与其他技术指标和价格分析一起使用，以提高交易决策的准确性。
 *
 */
public class AmountIndicator extends CachedIndicator<Num> {

    public AmountIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        return getBarSeries().getBar(index).getAmount();
    }
}