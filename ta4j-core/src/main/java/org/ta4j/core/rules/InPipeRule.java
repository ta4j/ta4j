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
 * Indicator-between-indicators rule.
 * * 指标间指标规则。
 *
 * Satisfied when the value of the {@link Indicator indicator} is between the values of the boundary (up/down) indicators.
 * * 当 {@link Indicator indicator} 的值在边界（上/下）指标的值之间时满足。
 */
public class InPipeRule extends AbstractRule {

    /** The upper indicator
     * 上指标 */
    private Indicator<Num> upper;
    /** The lower indicator
     * 较低的指标*/
    private Indicator<Num> lower;
    /** The evaluated indicator
     * 评估指标 */
    private Indicator<Num> ref;

    /**
     * Constructor.
     * 
     * @param ref   the reference indicator
     *              参考指标
     * @param upper the upper threshold
     *              上限
     * @param lower the lower threshold
     *              下限
     */
    public InPipeRule(Indicator<Num> ref, Number upper, Number lower) {
        this(ref, ref.numOf(upper), ref.numOf(lower));
    }

    /**
     * Constructor.
     * 
     * @param ref   the reference indicator
     *              参考指标
     * @param upper the upper threshold
     *              上限
     * @param lower the lower threshold
     *              下限
     */
    public InPipeRule(Indicator<Num> ref, Num upper, Num lower) {
        this(ref, new ConstantIndicator<>(ref.getBarSeries(), upper),
                new ConstantIndicator<>(ref.getBarSeries(), lower));
    }

    /**
     * Constructor.
     * 
     * @param ref   the reference indicator
     *              参考指标
     * @param upper the upper indicator
     *              上指标
     * @param lower the lower indicator
     *              较低的指标
     */
    public InPipeRule(Indicator<Num> ref, Indicator<Num> upper, Indicator<Num> lower) {
        this.upper = upper;
        this.lower = lower;
        this.ref = ref;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final boolean satisfied = ref.getValue(index).isLessThanOrEqual(upper.getValue(index))
                && ref.getValue(index).isGreaterThanOrEqual(lower.getValue(index));
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
