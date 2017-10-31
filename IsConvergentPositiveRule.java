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
import org.ta4j.core.indicators.helpers.MinPriceIndicator;
import org.ta4j.core.trading.rules.AbstractRule;

/**
 * Indicator-convergent-positive rule.
 * <p>
 * Satisfied when the values of the {@link Indicator indicator} strict increase
 * or is the highest within the timeFrame and the values of the other
 * {@link Indicator indicator} strict increase or is the highest or is not the
 * highest within a timeFrame. In short: "other" and "ref" makes higher highs.
 * It can test both, strict and non-strict divergence.
 */
public class IsConvergentPositiveRule extends AbstractRule {

	/** The rule for positive divergence */
	private final Rule isConvergentPositive;

	/**
	 * Constructor. <br/>
	 * 
	 * If "useStrictConvergence" is enabled, convergence is satisfied if values
	 * of "ref" and the values of the "other" indicator strict increase.
	 * 
	 * @param ref the indicator
	 * @param other the other indicator
	 * @param timeFrame
	 * @param useStrictConvergence
	 */
	public IsConvergentPositiveRule(Indicator<Decimal> ref, Indicator<Decimal> other, int timeFrame,
			boolean useStrictConvergence) {

		Rule refIsRising = new IsRisingRule(ref, timeFrame);
		Rule otherIsRising = new IsRisingRule(other, timeFrame);

		if (useStrictConvergence) {
			isConvergentPositive = refIsRising.and(otherIsRising);
		} else {

			Rule refIsHighest = new IsHighestRule(ref, timeFrame);
			Rule otherIsHighest = new IsHighestRule(other, timeFrame);
			Rule otherIsNotHighest = new IsHighestRule(other, timeFrame).negation();

			isConvergentPositive = (refIsRising.or(refIsHighest))
					.and(otherIsRising.or(otherIsHighest).or(otherIsNotHighest));
		}
	}
	
	/**
	 * Constructor.
	 * 
	 * Tests for positive convergence between ref and MinPriceIndicator.
	 * 
	 * @param series
	 * @param ref the indicator
	 * @param timeFrame
	 */
	public IsConvergentPositiveRule(TimeSeries series, Indicator<Decimal> ref, int timeFrame) {
		this(ref, new MinPriceIndicator(series), timeFrame, false);
	}

	@Override
	public boolean isSatisfied(int index, TradingRecord tradingRecord) {
		
		final boolean satisfied = isConvergentPositive.isSatisfied(index, tradingRecord);
		traceIsSatisfied(index, satisfied);
		return satisfied;
	}
}