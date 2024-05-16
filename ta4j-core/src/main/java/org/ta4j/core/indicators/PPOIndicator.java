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
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Percentage price oscillator (PPO) indicator. <br/>
  Aka. MACD Percentage Price Oscillator (MACD-PPO).
 百分比价格振荡器 (PPO) 指标。 <br/>
 阿卡。 MACD 百分比价格震荡指标 (MACD-PPO)。


 百分比价格振荡器（Percentage Price Oscillator，PPO）是一种基于价格移动平均线的技术指标，它衡量了两条移动平均线之间的百分比差异，并据此提供价格趋势和买卖信号。

 PPO的计算过程类似于MACD指标，但是它使用百分比差异而不是绝对差异来表示移动平均线之间的距离。以下是计算PPO的一般步骤：

 1. 计算较长期移动平均线：通常选择一个较长的周期（例如26个交易周期）来计算较长期的移动平均线。
 2. 计算较短期移动平均线：选择一个较短的周期（例如12个交易周期）来计算较短期的移动平均线。
 3. 计算PPO线：将较短期移动平均线减去较长期移动平均线，然后再除以较长期移动平均线，并乘以100以得到百分比差异。
 4. 计算PPO的信号线：通常采用PPO线的9个周期移动平均线作为PPO的信号线。

 PPO指标的数值通常在零线上下波动，正值表示较短期移动平均线高于较长期移动平均线，可能暗示着价格上涨趋势；负值表示较短期移动平均线低于较长期移动平均线，可能暗示着价格下跌趋势。

 交易者通常使用PPO来识别价格的趋势转折点和制定买卖信号。例如，当PPO线上穿信号线时，可能暗示着价格的上升趋势加强，为买入信号；相反，当PPO线下穿信号线时，可能暗示着价格的下跌趋势加强，为卖出信号。

 总的来说，PPO是一种用于识别价格趋势和买卖信号的常用技术指标，但仍建议将其与其他技术指标和价格模式结合使用，以增强交易决策的准确性。
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/p/ppo.asp">https://www.investopedia.com/terms/p/ppo.asp</a>
 */
public class PPOIndicator extends CachedIndicator<Num> {

    private final EMAIndicator shortTermEma;
    private final EMAIndicator longTermEma;

    /**
     * Constructor with shortBarCount "12" and longBarCount "26".
     * shortBarCount "12" 和 longBarCount "26" 的构造函数。
     *
     * @param indicator the indicator
     *                  指标
     */
    public PPOIndicator(Indicator<Num> indicator) {
        this(indicator, 12, 26);
    }

    /**
     * Constructor.
     *
     * @param indicator     the indicator
     *                      指标
     * @param shortBarCount the short time frame
     *                      短时间(範圍)框架
     * @param longBarCount  the long time frame
     *                      长時間(範圍)框架
     */
    public PPOIndicator(Indicator<Num> indicator, int shortBarCount, int longBarCount) {
        super(indicator);
        if (shortBarCount > longBarCount) {
            throw new IllegalArgumentException("Long term period count must be greater than short term period count 长期周期计数必须大于短期周期计数");
        }
        this.shortTermEma = new EMAIndicator(indicator, shortBarCount);
        this.longTermEma = new EMAIndicator(indicator, longBarCount);
    }

    @Override
    protected Num calculate(int index) {
        Num shortEmaValue = shortTermEma.getValue(index);
        Num longEmaValue = longTermEma.getValue(index);
        return shortEmaValue.minus(longEmaValue).dividedBy(longEmaValue).multipliedBy(numOf(100));
    }
}
