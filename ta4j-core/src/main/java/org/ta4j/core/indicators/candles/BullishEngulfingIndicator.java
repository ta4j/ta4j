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
 * Bullish engulfing pattern indicator.
 * 看涨吞没形态指标。
 *
 * Bullish Engulfing Pattern（看涨吞没形态）是技术分析中的一种反转形态，通常出现在下降趋势的底部，预示着可能的趋势反转向上。该形态由两根蜡烛线组成，第一根是较小的阴线（或阳线），第二根是较大的阳线，第二根蜡烛线的实体完全吞没了第一根蜡烛线的实体。
 *
 * ### 特征
 * 1. **第一天**：一根较小的阴线（或阳线），通常出现在下降趋势中。
 * 2. **第二天**：一根较大的阳线，其开盘价低于前一天的收盘价，收盘价高于前一天的开盘价，即第二根阳线的实体完全吞没了第一根阴线的实体。
 *
 * ### 识别方法
 * 要识别看涨吞没形态，可以通过以下步骤：
 * 1. **确认下降趋势**：在形成看涨吞没形态之前，市场应处于下降趋势或有一段时间的下跌。
 * 2. **第一根蜡烛线**：一根较小的阴线（或阳线），表示卖方力量减弱。
 * 3. **第二根蜡烛线**：一根较大的阳线，开盘价低于前一根蜡烛线的收盘价，收盘价高于前一根蜡烛线的开盘价。
 *
 * ### 交易策略
 * 1. **入场点**：当看涨吞没形态形成后，可以在第二根阳线收盘后或第三根蜡烛线开始时考虑买入或做多。
 * 2. **止损位**：将止损位设置在看涨吞没形态的最低点下方，防止假信号带来的损失。
 * 3. **目标位**：目标价位可以设在前一个阻力位，或根据风险回报比设定。
 *
 * ### 示例
 * 假设市场处于下降趋势中：
 * - **第一天**：形成一根较小的阴线，显示卖方力量减弱。
 * - **第二天**：形成一根较大的阳线，开盘价低于前一天的收盘价，但收盘价高于前一天的开盘价，完全吞没了第一天的阴线。
 *
 * 这时，可以在第二天收盘后或第三天开盘时考虑买入，止损位设在看涨吞没形态的最低点下方，目标价位设在前一个阻力位附近。
 *
 * ### 注意事项
 * - **趋势确认**：看涨吞没形态更有效于确认市场的趋势反转，因此在交易时应确认前期的下降趋势。
 * - **结合其他指标**：单独使用看涨吞没形态可能会产生误导信号，建议结合其他技术指标（如相对强弱指数 RSI、移动平均线 MACD）进行综合分析。
 * - **市场环境**：不同市场和时间框架下，看涨吞没形态的表现可能有所不同，需根据具体情况调整策略。
 *
 * ### 总结
 * 看涨吞没形态是一种强有力的反转信号，通常出现在下降趋势的底部，通过识别两根特定的蜡烛线来预示市场可能的反转。结合其他技术指标和市场分析方法，可以提高交易决策的准确性。在实际应用中，交易者应根据具体市场环境和自身风险承受能力进行综合判断。
 *
 * @see <a href=
 *      "http://www.investopedia.com/terms/b/bullishengulfingpattern.asp">
 *      http://www.investopedia.com/terms/b/bullishengulfingpattern.asp</a>
 */
public class BullishEngulfingIndicator extends CachedIndicator<Boolean> {

    /**
     * Constructor.
     *
     * @param series a bar series
     */
    public BullishEngulfingIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < 1) {
            // Engulfing is a 2-candle pattern
            // 吞没是一个 2 蜡烛形态
            return false;
        }
        Bar prevBar = getBarSeries().getBar(index - 1);
        Bar currBar = getBarSeries().getBar(index);
        if (prevBar.isBearish() && currBar.isBullish()) {
            final Num prevOpenPrice = prevBar.getOpenPrice();
            final Num prevClosePrice = prevBar.getClosePrice();
            final Num currOpenPrice = currBar.getOpenPrice();
            final Num currClosePrice = currBar.getClosePrice();
            return currOpenPrice.isLessThan(prevOpenPrice) && currOpenPrice.isLessThan(prevClosePrice)
                    && currClosePrice.isGreaterThan(prevOpenPrice) && currClosePrice.isGreaterThan(prevClosePrice);
        }
        return false;
    }
}
