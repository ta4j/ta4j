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
import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.trading.rules.AbstractRule;

/**
 * Indicator-convergent-negative rule.
 * <p>
 * Satisfied when the values of the {@link Indicator indicator} strict decrease
 * or is the lowest or is not the highest within the timeFrame and the values of
 * the other {@link Indicator indicator} strict decrease or is the lowest within
 * the timeFrame. In short: "other" and "ref" makes lower lows. It can test
 * both, strict and non-strict convergence.
 */
public class IsConvergentNegativeRule extends AbstractRule {

	/** The rule for negative convergence */
	private final Rule isConvergentNegative;
	/**
	 * Constructor. <br/>
	 * 
	 * If "useStrictConvergence" is enabled, convergence is satisfied if values of
	 * "ref" and values of the "other" indicator strict decrease.
	 * 
	 * @param ref the indicator
	 * @param other the other indicator
	 * @param timeFrame
	 * @param useStrictConvergence
	 */
	public IsConvergentNegativeRule(Indicator<Decimal> ref, Indicator<Decimal> other, int timeFrame,
			boolean useStrictConvergence) {
		
		Rule refIsFalling = new IsFallingRule(ref, timeFrame);
		Rule otherIsFalling = new IsFallingRule(other, timeFrame);

		if (useStrictConvergence) {
			isConvergentNegative = refIsFalling.and(otherIsFalling);
		} else {

			Rule refIsLowest = new IsLowestRule(ref, timeFrame);
			Rule otherIsLowest = new IsLowestRule(other, timeFrame);
			Rule refIsNotHighest = new IsHighestRule(ref, timeFrame).negation();

			isConvergentNegative = (refIsFalling.or(refIsLowest).or(refIsNotHighest))
					.and(otherIsFalling.or(otherIsLowest));
		}
	}
	
	/**
	 * Constructor.
	 * 
	 * Rule for negative convergence between ref and MaxPriceIndicator.
	 * 
	 * @param series
	 * @param ref the indicator
	 * @param timeFrame
	 */
	public IsConvergentNegativeRule(TimeSeries series, Indicator<Decimal> ref, int timeFrame) {
		this(ref, new MaxPriceIndicator(series), timeFrame, false);
	}

	@Override
	public boolean isSatisfied(int index, TradingRecord tradingRecord) {
		
		final boolean satisfied = isConvergentNegative.isSatisfied(index, tradingRecord);
		traceIsSatisfied(index, satisfied);
		return satisfied;
	}
}