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

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Indicator-in-slope rule.
 * 斜率指标规则。
 *
 * Satisfied when the difference of the value of the {@link Indicator indicator}
  and the previous (n-th) value of the {@link Indicator indicator} is between
  the values of maxSlope or/and minSlope. It can test both, positive and
  negative slope.
 当 {@link Indicator indicator} 的值的差异时满足
 {@link Indicator indicator} 的前一个（第 n 个）值介于
 maxSlope 或/和 minSlope 的值。 它可以同时测试，阳性和
 负斜率。
 */
public class InSlopeRule extends AbstractRule {

    /** The actual indicator
     * 实际指标 */
    private Indicator<Num> ref;
    /** The previous n-th value of ref
     * ref 的前 n 个值 */
    private PreviousValueIndicator prev;
    /** The minimum slope between ref and prev
     * ref 和 prev 之间的最小斜率 */
    private Num minSlope;
    /** The maximum slope between ref and prev
     * ref 和 prev 之间的最大斜率 */
    private Num maxSlope;

    /**
     * Constructor.
     * 
     * @param ref      the reference indicator
     *                 参考指标
     * @param minSlope minumum slope between reference and previous indicator
     *                 参考指标和前一个指标之间的最小斜率
     */
    public InSlopeRule(Indicator<Num> ref, Num minSlope) {
        this(ref, 1, minSlope, NaN);
    }

    /**
     * Constructor.
     * 
     * @param ref      the reference indicator
     *                 参考指标
     * @param minSlope minumum slope between value of reference and previous    indicator
     *                 参考值与前一个指标之间的最小斜率
     *
     * @param maxSlope maximum slope between value of reference and previous   indicator
     *                 参考值与前一个指标之间的最大斜率
     */
    public InSlopeRule(Indicator<Num> ref, Num minSlope, Num maxSlope) {
        this(ref, 1, minSlope, maxSlope);
    }

    /**
     * Constructor.
     * 
     * @param ref         the reference indicator
     *                    参考指标
     * @param nthPrevious defines the previous n-th indicator
     *                    定义前第 n 个指标
     * @param maxSlope    maximum slope between value of reference and previous  indicator
     *                    参考值与前一个指标之间的最大斜率
     */
    public InSlopeRule(Indicator<Num> ref, int nthPrevious, Num maxSlope) {
        this(ref, nthPrevious, NaN, maxSlope);
    }

    /**
     * Constructor.
     * 
     * @param ref         the reference indicator
     *                    参考指标
     * @param nthPrevious defines the previous n-th indicator
     *                    定义前第 n 个指标
     * @param minSlope    minumum slope between value of reference and previous   indicator
     *                    参考值与前一个指标之间的最小斜率
     *
     * @param maxSlope    maximum slope between value of reference and previous  indicator
     *                    参考值与前一个指标之间的最大斜率
     */
    public InSlopeRule(Indicator<Num> ref, int nthPrevious, Num minSlope, Num maxSlope) {
        this.ref = ref;
        this.prev = new PreviousValueIndicator(ref, nthPrevious);
        this.minSlope = minSlope;
        this.maxSlope = maxSlope;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        DifferenceIndicator diff = new DifferenceIndicator(ref, prev);
        Num val = diff.getValue(index);
        boolean minSlopeSatisfied = minSlope.isNaN() || val.isGreaterThanOrEqual(minSlope);
        boolean maxSlopeSatisfied = maxSlope.isNaN() || val.isLessThanOrEqual(maxSlope);
        boolean isNaN = minSlope.isNaN() && maxSlope.isNaN();

        final boolean satisfied = minSlopeSatisfied && maxSlopeSatisfied && !isNaN;
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
