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
 * Indicator-divergent-positive rule ("bullish divergence").
 * <p>
 * Satisfied when the values of the {@link Indicator indicator} strict increase
 * or is the highest within the timeFrame and the values of the other
 * {@link Indicator indicator} strict decrease or is the lowest or is not the
 * highest within a timeFrame. In short: "other" makes lower lows while "ref"
 * makes higher lows. It can test both, strict and non-strict divergence.
 */
public class IsDivergentPositiveRule extends AbstractRule {

	/** The rule for positive divergence */
	private final Rule isDivergentPositive;

	/**
	 * Constructor. <br/>
	 * 
	 * If "useStrictDivergence" is enabled, divergence is satisfied if values of
	 * "ref" strict increase while the values of the "other" indicator strict
	 * decrease.
	 * 
	 * @param ref the indicator
	 * @param other the other indicator
	 * @param timeFrame
	 * @param useStrictDivergence
	 */
	public IsDivergentPositiveRule(Indicator<Decimal> ref, Indicator<Decimal> other, int timeFrame,
			boolean useStrictDivergence) {

		Rule refIsRising = new IsRisingRule(ref, timeFrame);
		Rule otherIsFalling = new IsFallingRule(other, timeFrame);

		if (useStrictDivergence) {
			isDivergentPositive = refIsRising.and(otherIsFalling);
		} else {

			Rule refIsHighest = new IsHighestRule(ref, timeFrame);
			Rule otherIsLowest = new IsLowestRule(other, timeFrame);
			Rule refIsNotLowest = new IsLowestRule(other, timeFrame).negation();

			isDivergentPositive = (refIsRising.or(refIsHighest).or(refIsNotLowest))
					.and(otherIsFalling.or(otherIsLowest));
		}
	}
	
	/**
	 * Constructor.
	 * 
	 * Tests for positive divergence between ref and MinPriceIndicator.
	 * 
	 * @param series
	 * @param ref the indicator
	 * @param timeFrame
	 */
	public IsDivergentPositiveRule(TimeSeries series, Indicator<Decimal> ref, int timeFrame) {
		this(ref, new MinPriceIndicator(series), timeFrame, false);
	}

	@Override
	public boolean isSatisfied(int index, TradingRecord tradingRecord) {
		
		final boolean satisfied = isDivergentPositive.isSatisfied(index, tradingRecord);
		traceIsSatisfied(index, satisfied);
		return satisfied;
	}
}
