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
 * Sum indicator.
 * 总数指标。
 *
 * I.e.: operand0 + operand1 + ... + operandN
 * 即：操作数0 +操作数1 + ... +操作数N
 *
 * "Sum indicator" 是指在技术分析中用于计算一组数据的总和的指标。这个指标的计算非常简单，只是将数据集中所有数据的值相加。
 *
 * 例如，如果你有一组包含五个数据的数据集：
 *
 * \[ [10, 15, 20, 25, 30] \]
 *
 * 那么这个数据集的总和指标就是：
 *
 * \[ 10 + 15 + 20 + 25 + 30 = 100 \]
 *
 * 在金融市场分析中，总和指标可以用于多种情况，包括但不限于：
 *
 * 1. **移动平均线计算**：总和指标是计算简单移动平均线（SMA）或加权移动平均线（WMA）时的一部分。通过计算一段时间内的价格总和，然后除以期间的长度，可以得到移动平均线的值。
 *
 * 2. **成交量分析**：在股票交易中，总和指标可以用于计算某一段时间内的成交量总和。这有助于分析市场的活跃程度和资金流入流出情况。
 *
 * 3. **价格变化指标**：总和指标也可以用于计算价格变化或波动的总和，从而帮助分析市场的波动性和价格变化趋势。
 *
 * 总的来说，总和指标是一种简单但常用的技术分析工具，用于计算一组数据的总和，从而提供有关市场走势和价格变化的信息。
 *
 *
 */
public class SumIndicator extends CachedIndicator<Num> {

    private final Indicator<Num>[] operands;

    /**
     * Constructor. (operand0 plus operand1 plus ... plus operandN)
     * * 构造函数。 （操作数 0 加上操作数 1 加上 ... 加上操作数 N）
     * 
     * @param operands the operand indicators for the sum
     *                 * @param operands 求和的操作数指示符
     */
    @SafeVarargs
    public SumIndicator(Indicator<Num>... operands) {
        // TODO: check if first series is equal to the other ones
        // TODO: 检查第一个系列是否等于其他系列
        super(operands[0]);
        this.operands = operands;
    }

    @Override
    protected Num calculate(int index) {
        Num sum = numOf(0);
        for (Indicator<Num> operand : operands) {
            sum = sum.plus(operand.getValue(index));
        }
        return sum;
    }
}
