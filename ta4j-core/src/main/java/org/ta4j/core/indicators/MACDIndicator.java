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
 * Moving average convergence divergence (MACDIndicator) indicator. <br/>
 * 移动平均收敛散度 (MACDIndicator) 指标。 <br/>
 * Aka. MACD Absolute Price Oscillator (APO).
 * 阿卡。 MACD 绝对价格震荡指标 (APO)。
 *
 *
 * 移动平均线收敛背离指标（Moving Average Convergence Divergence，MACD）是一种常用的技术指标，用于识别价格动量、趋势变化以及买卖信号。MACD由两条移动平均线及其差值组成，通常包括快速线（DIF）和慢速线（DEA），以及它们的差值（MACD柱）。
 *
 * MACD指标的计算过程如下：
 *
 * 1. 计算快速线（DIF）：通常是短期（例如12个周期）移动平均线减去长期（例如26个周期）移动平均线。
 * 2. 计算慢速线（DEA）：对快速线进行平滑处理，通常采用9个周期的移动平均线。
 * 3. 计算MACD柱：将快速线与慢速线之间的差值绘制成柱状图，即为MACD柱。
 *
 * MACD指标的数值通常波动在零线上下。当快速线上穿慢速线时，MACD柱从负值转为正值，形成“金叉”，可能暗示着价格的上升趋势加强，为买入信号；相反，当快速线下穿慢速线时，MACD柱从正值转为负值，形成“死叉”，可能暗示着价格的下跌趋势加强，为卖出信号。
 *
 * MACD指标可以用于识别价格的趋势转折点、确认价格趋势以及制定买卖信号。然而，MACD指标的解释和使用需要一定的经验和技术分析知识，因为它对价格的反应速度较快，容易产生虚假信号，需要结合其他指标和价格模式进行综合分析。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:moving_average_convergence_divergence_macd">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:moving_average_convergence_divergence_macd</a>
 */
public class MACDIndicator extends CachedIndicator<Num> {

    private final EMAIndicator shortTermEma;
    private final EMAIndicator longTermEma;

    /**
     * Constructor with shortBarCount "12" and longBarCount "26".
     * shortBarCount "12" 和 longBarCount "26" 的构造函数。
     *
     * @param indicator the indicator
     *                  指标
     */
    public MACDIndicator(Indicator<Num> indicator) {
        this(indicator, 12, 26);
    }

    /**
     * Constructor.
     *
     * @param indicator     the indicator
     *                      指标
     * @param shortBarCount the short time frame (normally 12)
     *                      短时间框架（通常为 12）
     * @param longBarCount  the long time frame (normally 26)
     *                      较长的时间范围（通常为 26）
     */
    public MACDIndicator(Indicator<Num> indicator, int shortBarCount, int longBarCount) {
        super(indicator);
        if (shortBarCount > longBarCount) {
            throw new IllegalArgumentException("Long term period count must be greater than short term period count 长期周期计数必须大于短期周期计数");
        }
        shortTermEma = new EMAIndicator(indicator, shortBarCount);
        longTermEma = new EMAIndicator(indicator, longBarCount);
    }

    /**
     * Short term EMA indicator
     * * 短期 EMA 指标
     *
     * @return the Short term EMA indicator
     * @return 短期 EMA 指标
     */
    public EMAIndicator getShortTermEma() {
        return shortTermEma;
    }

    /**
     * Long term EMA indicator
     * 长期EMA指标
     *
     * @return the Long term EMA indicator
     * @return 长期 EMA 指标
     */
    public EMAIndicator getLongTermEma() {
        return longTermEma;
    }

    @Override
    protected Num calculate(int index) {
        return shortTermEma.getValue(index).minus(longTermEma.getValue(index));
    }
}
