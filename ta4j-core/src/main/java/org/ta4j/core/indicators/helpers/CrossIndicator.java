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

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Cross indicator.
 * 交叉指标。
 *
 * Boolean indicator which monitors two-indicators crossings.
 * 监控两个指标交叉点的布尔指标。
 *
 * "Cross indicator" 并不是一个特定的技术分析指标，但这个术语可能指的是两条线或曲线在图表上交叉的情况，通常用于判断买入或卖出的信号。
 *
 * 在技术分析中，线的交叉可能代表价格走势的变化或者趋势的转折，这种交叉可以是两个移动平均线、指标线与价格线的交叉等。不同的交叉方式可能有不同的含义，例如：
 *
 * - **均线交叉**：当短期移动平均线上穿长期移动平均线时，被称为“金叉”，可能表示买入信号；而当短期移动平均线下穿长期移动平均线时，被称为“死叉”，可能表示卖出信号。
 * - **价格线与指标线交叉**：当价格线与某个指标线（如MACD、RSI等）交叉时，也可能产生买入或卖出的信号，具体取决于交叉的方向和市场环境。
 *
 * 因此，"Cross indicator" 可能指的是利用线的交叉来产生交易信号的一种技术分析方法。在使用这种方法时，通常需要结合其他指标和分析工具，以及考虑市场的整体环境和趋势。
 *
 * ==================================================================
 *  第二种解释
 * ==================================================================
 *
 * "Cross indicator" 通常指的是一种技术分析指标，它基于两条或多条线（如移动平均线、振荡器等）的交叉点来生成交易信号。当这些线在图表上交叉时，它们可能会产生买入或卖出的信号。以下是一些常见的基于交叉的交易指标：
 *
 * 移动平均线交叉：
 * 当较短的移动平均线（如5日均线）上穿较长的移动平均线（如20日均线）时，这通常被视为一个买入信号（金叉）。
 * 当较短的移动平均线下穿较长的移动平均线时，这通常被视为一个卖出信号（死叉）。
 * MACD 交叉：
 * MACD（移动平均收敛/发散）指标包括一个DIF线和一个DEA线（或称为信号线）。当DIF线上穿DEA线时，可能产生买入信号。当DIF线下穿DEA线时，可能产生卖出信号。
 * 随机指标（Stochastic Oscillator）交叉：
 * 随机指标包含两条线，%K线和%D线。当%K线上穿%D线时，可能产生买入信号。当%K线下穿%D线时，可能产生卖出信号。
 * RSI（相对强弱指数）交叉：
 * 虽然RSI本身不是一个基于交叉的指标，但一些交易者会观察RSI线与其过去某个水平的交叉来生成交易信号。例如，当RSI从下方上穿30线时，可能被视为买入信号；从上方下穿70线时，可能被视为卖出信号。
 * 其他振荡器交叉：
 * 其他类型的振荡器，如威廉指标（Williams %R）、动量振荡器等，也可能基于它们的线之间的交叉来生成交易信号。
 * 在使用基于交叉的交易指标时，重要的是要注意市场条件的变化和指标的滞后性。此外，与其他技术指标和基本面分析结合使用，可以提高交易决策的准确性。最后，始终要谨慎管理风险，不要仅依赖一个指标进行交易决策。
 *
 */
public class CrossIndicator extends CachedIndicator<Boolean> {

    /** Upper indicator 上指标 */
    private final Indicator<Num> up;
    /** Lower indicator 下指标 */
    private final Indicator<Num> low;

    /**
     * Constructor.
     * 构造函数
     *
     * @param up  the upper indicator 上指标
     * @param low the lower indicator 较低的指标
     */
    public CrossIndicator(Indicator<Num> up, Indicator<Num> low) {
        // TODO: check if up series is equal to low series
        super(up);
        this.up = up;
        this.low = low;
    }

    @Override
    protected Boolean calculate(int index) {

        int i = index;
        if (i == 0 || up.getValue(i).isGreaterThanOrEqual(low.getValue(i))) {
            return false;
        }

        i--;
        if (up.getValue(i).isGreaterThan(low.getValue(i))) {
            return true;
        }
        while (i > 0 && up.getValue(i).isEqual(low.getValue(i))) {
            i--;
        }
        return (i != 0) && (up.getValue(i).isGreaterThan(low.getValue(i)));
    }

    /**
     * @return the initial lower indicator
     * @return 初始下限指标
     */
    public Indicator<Num> getLow() {
        return low;
    }

    /**
     * @return the initial upper indicator
     * @return 初始上限指标
     */
    public Indicator<Num> getUp() {
        return up;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + low + " " + up;
    }
}
