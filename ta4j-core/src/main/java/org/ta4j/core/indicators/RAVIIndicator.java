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
 * Chande's Range Action Verification Index (RAVI) indicator.
 * Chande 的范围行动验证指数 (RAVI) 指标。
 * 
 * To preserve trend direction, default calculation does not use absolute value.
 * 为了保持趋势方向，默认计算不使用绝对值。
 *
 *
 * 尚德的范围动作验证指数（Chande's Range Action Verification Index，RAVI）是一种技术指标，用于衡量价格波动的强度和趋势的方向。它由Tushar Chande开发，旨在帮助交易者确认价格趋势的可靠性。
 *
 * RAVI指标通过比较两个不同期间内的价格范围来评估价格波动的强度。具体来说，RAVI指标计算短期和长期价格范围的指数加权移动平均线（EMA）之间的差异，并据此提供价格趋势的信号。
 *
 * RAVI指标的计算过程如下：
 *
 * 1. 计算短期和长期价格范围的指数加权移动平均线（EMA）。通常选择短期为7个交易周期，长期为65个交易周期。
 * 2. 计算短期EMA和长期EMA之间的差值。
 * 3. RAVI线：用短期EMA减去长期EMA，即为RAVI线。
 *
 * RAVI指标的数值可以为正值或负值，正值表示短期价格波动幅度大于长期价格波动幅度，可能暗示着价格的上涨趋势；负值表示短期价格波动幅度小于长期价格波动幅度，可能暗示着价格的下跌趋势。
 *
 * 交易者通常使用RAVI指标来确认价格趋势的可靠性和制定买卖信号。例如，当RAVI线从负值转为正值时，可能暗示着价格的上涨趋势加强，为买入信号；相反，当RAVI线从正值转为负值时，可能暗示着价格的下跌趋势加强，为卖出信号。
 *
 * 总的来说，RAVI指标是一种用于确认价格趋势和提供买卖信号的技术指标，可以帮助交易者更好地理解价格波动的强度和趋势的方向。
 *
 */
public class RAVIIndicator extends CachedIndicator<Num> {

    private final SMAIndicator shortSma;
    private final SMAIndicator longSma;

    /**
     * Constructor.
     * 
     * @param price            the price
     *                         价格
     *
     * @param shortSmaBarCount the time frame for the short SMA (usually 7)
     *                         短 SMA 的时间框架（通常为 7）
     *
     * @param longSmaBarCount  the time frame for the long SMA (usually 65)
     *                         多头 SMA 的时间框架（通常为 65）
     */
    public RAVIIndicator(Indicator<Num> price, int shortSmaBarCount, int longSmaBarCount) {
        super(price);
        shortSma = new SMAIndicator(price, shortSmaBarCount);
        longSma = new SMAIndicator(price, longSmaBarCount);
    }

    @Override
    protected Num calculate(int index) {
        Num shortMA = shortSma.getValue(index);
        Num longMA = longSma.getValue(index);
        return shortMA.minus(longMA).dividedBy(longMA).multipliedBy(numOf(100));
    }
}
