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

/**
 * A fixed boolean indicator.
 * 一个固定的布尔指标。
 *
 * 固定布尔指标可能指的是一种技术分析指标，根据固定的规则或条件生成二元（布尔）信号。这些指标通常根据是否满足特定条件来产生买入或卖出信号。
 * 一个例子是简单移动平均线交叉策略。在这个策略中，使用两个不同周期的移动平均线（例如，50天和200天移动平均线）。当较短期的移动平均线向上穿越较长期的移动平均线时，它会产生一个买入信号（true），当它向下穿越较长期的移动平均线时，它会产生一个卖出信号（false）。
 *
 * 另一个例子可能是基于振荡器（如相对强弱指标RSI）的固定阈值指标。当RSI上穿某一阈值（例如70）时，它会产生一个卖出信号（false），当RSI下穿另一个阈值（例如30）时，它会产生一个买入信号（true）。
 *
 * 这些固定布尔指标相对简单直接，容易实施，因此在交易者中颇受欢迎，特别是那些喜欢机械交易系统的人。然而，需要注意的是，仅依靠固定规则而不考虑更广泛的市场背景或使用其他分析技术可能存在局限性和潜在缺点。
 *
 *
 */
public class FixedBooleanIndicator extends FixedIndicator<Boolean> {

    /**
     * Constructor.
     * 构造函数
     * 
     * @param values the values to be returned by this indicator
     *               该指标要返回的值
     */
    public FixedBooleanIndicator(BarSeries series, Boolean... values) {
        super(series, values);
    }
}
