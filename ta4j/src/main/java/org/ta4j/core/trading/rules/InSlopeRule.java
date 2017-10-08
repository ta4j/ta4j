/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package org.ta4j.core.trading.rules;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;

/**
 * Indicator-in-slope rule.
 * <p>
 * Satisfied when the difference of the value of the {@link Indicator indicator}
 * and the previous (n-th) value of the {@link Indicator indicator} is between the values of
 * maxSlope or/and minSlope. It can test both, positive and negative slope.
 */
public class InSlopeRule extends AbstractRule {

    /** The actual indicator */
    private Indicator<Decimal> ref;
    /** The previous n-th value of ref */
    private PreviousValueIndicator prev;
    /** The minimum slope between ref and prev */
    private Decimal minSlope;
    /** The maximum slope between ref and prev */
    private Decimal maxSlope;

    /**
     * Constructor.
     * @param ref the reference indicator
     * @param minSlope minumum slope between reference and previous indicator
     */
    public InSlopeRule(Indicator<Decimal> ref, Decimal minSlope) {
        this(ref, 1, minSlope, Decimal.NaN);
    }
    
    /**
     * Constructor.
     * @param ref the reference indicator
     * @param minSlope minumum slope between value of reference and previous indicator
     * @param maxSlope maximum slope between value of reference and previous indicator
     */
    public InSlopeRule(Indicator<Decimal> ref, Decimal minSlope, Decimal maxSlope) {
        this(ref, 1, minSlope, maxSlope);
    }
    
     /**
     * Constructor.
     * @param ref the reference indicator
     * @param nthPrevious defines the previous n-th indicator
     * @param maxSlope maximum slope between value of reference and previous indicator
     */
    public InSlopeRule(Indicator<Decimal> ref, int nthPrevious, Decimal maxSlope) {
    	this(ref, nthPrevious, Decimal.NaN, maxSlope);
    }

    /**
     * Constructor.
     * @param ref the reference indicator
     * @param nthPrevious defines the previous n-th indicator
     * @param minSlope minumum slope between value of reference and previous indicator
     * @param maxSlope maximum slope between value of reference and previous indicator
     */
    public InSlopeRule(Indicator<Decimal> ref, int nthPrevious, Decimal minSlope, Decimal maxSlope) {
        this.ref = ref;
        this.prev = new PreviousValueIndicator(ref, nthPrevious);
        this.minSlope = minSlope;
        this.maxSlope = maxSlope;
    }

   @Override
   public boolean isSatisfied(int index, TradingRecord tradingRecord) {
	DifferenceIndicator diff = new DifferenceIndicator(ref, prev);
	Decimal val = diff.getValue(index);
	boolean minSlopeSatisfied = minSlope.isNaN() ? true : val.isGreaterThanOrEqual(minSlope);
	boolean maxSlopeSatisfied = maxSlope.isNaN() ? true : val.isLessThanOrEqual(maxSlope);
	boolean isNaN = minSlope.isNaN() && maxSlope.isNaN();

	final boolean satisfied = minSlopeSatisfied && maxSlopeSatisfied && !isNaN;
	traceIsSatisfied(index, satisfied);
	return satisfied;
   }
}
