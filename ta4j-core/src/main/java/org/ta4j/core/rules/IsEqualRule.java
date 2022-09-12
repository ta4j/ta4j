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
package org.ta4j.core.rules;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;

/**
 * Indicator-equal-indicator rule.
 * 指标-等于-指标规则。
 *
 * Satisfied when the value of the first {@link Indicator indicator} is equal to the value of the second one.
 * * 当第一个 {@link Indicator indicator} 的值等于第二个的值时满足。
 */
public class IsEqualRule extends AbstractRule {

    /**
     * The first indicator
     * 第一个指标
     */
    private final Indicator<Num> first;
    /**
     * The second indicator
     * 第二个指标
     */
    private final Indicator<Num> second;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     *                  指标
     * @param value     the value to check
     *                  要检查的值
     */
    public IsEqualRule(Indicator<Num> indicator, Number value) {
        this(indicator, indicator.numOf(value));
    }

    /**
     * Constructor.
     *
     * @param indicator the indicator
     *                  指标
     * @param value     the value to check
     *                  要检查的值
     */
    public IsEqualRule(Indicator<Num> indicator, Num value) {
        this(indicator, new ConstantIndicator<>(indicator.getBarSeries(), value));
    }

    /**
     * Constructor.
     *
     * @param first  the first indicator
     *               第一个指标
     * @param second the second indicator
     *               第二个指标
     */
    public IsEqualRule(Indicator<Num> first, Indicator<Num> second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = first.getValue(index).isEqual(second.getValue(index));
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
