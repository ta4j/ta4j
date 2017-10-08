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
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.trading.rules.AbstractRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;

/**
 * Indicator-highest-indicator rule.
 * <p>
 * Satisfied when the value of the {@link Indicator indicator} is the highest
 * within the previous (n-th) values.
 */
public class IsHighestRule extends AbstractRule {

	/** The actual indicator */
	private Indicator<Decimal> ref;
	/** The previous n-th value of ref */
	private int nthPrevious;

	/**
	 * Constructor.
	 * 
	 * @param ref
	 * @param nthPrevious
	 */
	public IsHighestRule(Indicator<Decimal> ref, int nthPrevious) {
		this.ref = ref;
		this.nthPrevious = nthPrevious;
	}

	@Override
	public boolean isSatisfied(int index, TradingRecord tradingRecord) {
		Rule gt = new OverIndicatorRule(ref, new PreviousValueIndicator(ref));
		if (nthPrevious > 1) {
			for (int i = 1; i < nthPrevious; i++) { // i must start again at 1.
				PreviousValueIndicator prev = new PreviousValueIndicator(ref, i);
				gt = gt.and(new OverIndicatorRule(ref, new PreviousValueIndicator(prev)));
			}
		}
		final boolean satisfied = gt.isSatisfied(index, tradingRecord);
		traceIsSatisfied(index, satisfied);
		return satisfied;
	}
}
