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

import java.math.BigDecimal;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * A fixed decimal indicator.
 * 一个固定的小数指示符。
 *
 * 固定小数指标可能是指一种技术分析指标，其输出结果为固定的小数值。这种指标通常用于衡量价格变化、波动性或其他市场特征，并且其输出值的精度通常是固定的。
 * 一个例子是平均真实范围（Average True Range，ATR）指标。ATR指标通常用于衡量资产价格的波动性，其输出结果是一个正数，表示一段时间内价格的平均波动范围。例如，ATR值为2.5可能表示该资产在过去一段时间内的平均每天波动范围为2.5个价格单位。
 *
 * 另一个例子是移动平均线指标。移动平均线指标通常用于平滑价格数据并识别趋势方向，其输出结果是一个数值序列，表示不同时间段内的平均价格。例如，一个20日移动平均线的输出值可能是一个固定的小数，表示过去20个交易日的平均价格。
 *
 * 这些固定小数指标在技术分析中广泛使用，因为它们提供了对市场特征的定量衡量，帮助分析师和交易者更好地理解市场的走势和行为。
 */
public class FixedDecimalIndicator extends FixedIndicator<Num> {

    /**
     * Constructor.
     * 
     * @param values the values to be returned by this indicator
     *               该指标要返回的值
     */
    public FixedDecimalIndicator(BarSeries series, double... values) {
        super(series);
        for (double value : values) {
            addValue(numOf(value));
        }
    }

    /**
     * Constructor.
     * 构造函数
     * 
     * @param values the values to be returned by this indicator
     *               该指标要返回的值
     */
    public FixedDecimalIndicator(BarSeries series, String... values) {
        super(series);
        for (String value : values) {
            addValue(numOf(new BigDecimal(value)));
        }
    }
}
