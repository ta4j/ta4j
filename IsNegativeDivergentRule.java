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
 * Indicator-negative-divergent rule ("bearish divergence").
 * <p>
 * Satisfied when the values of the {@link Indicator indicator} strict decrease
 * or is the lowest or is not the highest within the timeFrame and the values of
 * the other {@link Indicator indicator} strict increase or is the highest or is
 * not the lowest within a timeFrame. It can test both, strict and non-strict
 * divergence.
 */
public class IsNegativeDivergentRule extends AbstractRule {

	/** The actual indicator */
	private Indicator<Decimal> ref;
	/** The other indicator */
	private Indicator<Decimal> other;
	/** The timeFrame */
	private int timeFrame;
	/** Test for strict divergence. */
	private boolean useStrictDivergence;

	/**
	 * Constructor. <br/>
	 * 
	 * If "useStrictDivergence" is enabled, divergence is satisfied if values of
	 * "ref" strict decrease while the values of the "other" indicator strict
	 * increase.
	 * 
	 * @param ref
	 * @param other
	 * @param timeFrame
	 * @param useStrictDivergence
	 */
	public IsNegativeDivergentRule(Indicator<Decimal> ref, Indicator<Decimal> other, int timeFrame,
			boolean useStrictDivergence) {
		this.ref = ref;
		this.other = other;
		this.timeFrame = timeFrame;
		this.useStrictDivergence = useStrictDivergence;
	}
	
	/**
	 * Constructor.
	 * 
	 * Rule for negative divergence between ref and MaxPriceIndicator.
	 * 
	 * @param series
	 * @param ref
	 * @param timeFrame
	 */
	public IsNegativeDivergentRule(TimeSeries series, Indicator<Decimal> ref, int timeFrame) {
		this(ref, new MaxPriceIndicator(series), timeFrame, false);
	}

	@Override
	public boolean isSatisfied(int index, TradingRecord tradingRecord) {
		Rule refIsFalling = new IsFallingRule(ref, timeFrame);
		Rule otherIsRising = new IsRisingRule(other, timeFrame);

		Rule isNegativeDivergent;

		if (useStrictDivergence) {
			isNegativeDivergent = refIsFalling.and(otherIsRising);
		} else {

			Rule refIsLowest = new IsLowestRule(ref, timeFrame);
			Rule otherIsHighest = new IsHighestRule(other, timeFrame);
			
			Rule refIsNotHighest = new IsHighestRule(ref, timeFrame).negation();
			Rule otherIsNotLowest = new IsLowestRule(other, timeFrame).negation();

			isNegativeDivergent = (refIsFalling.or(refIsLowest).or(refIsNotHighest))
					.and(otherIsRising.or(otherIsHighest).or(otherIsNotLowest));
		}

		final boolean satisfied = isNegativeDivergent.isSatisfied(index, tradingRecord);
		traceIsSatisfied(index, satisfied);
		return satisfied;
	}
}