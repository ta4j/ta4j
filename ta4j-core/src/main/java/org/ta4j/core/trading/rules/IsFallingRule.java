/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.trading.rules;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;

/**
 * Indicator-falling-indicator rule.
 * <p></p>
 * Satisfied when the values of the {@link Indicator indicator} decrease
 * within the timeFrame.
 */
public class IsFallingRule extends AbstractRule {

	/** The actual indicator */
	private Indicator<Decimal> ref;
	/** The timeFrame */
	private int timeFrame;
	/** The falling factor */
	private double fallingFactor;

	/**
	 * Constructor.
	 * 
	 * @param ref the indicator
	 * @param timeFrame the time frame
	 */
	public IsFallingRule(Indicator<Decimal> ref, int timeFrame) {
		this(ref, timeFrame, 1.0);
	}
	
	/**
	 * Constructor.
	 * 
	 * @param ref the indicator
	 * @param timeFrame the time frame
	 * @param fallingFactor the falling factor between '0' and '1' (e.g. '1' means strict falling)
	 */
	public IsFallingRule(Indicator<Decimal> ref, int timeFrame, double fallingFactor) {
		this.ref = ref;
		this.timeFrame = timeFrame;
		this.fallingFactor = fallingFactor;
	}

	@Override
	public boolean isSatisfied(int index, TradingRecord tradingRecord) {
		
		if (fallingFactor >= 1) {
			fallingFactor = 0.99;
		}
		
		int countFalling = 0;
		for (int i = Math.max(0, index - timeFrame + 1); i <= index; i++) {
			if (ref.getValue(i).isLessThan(ref.getValue(Math.max(0, i - 1)))) {
				countFalling += 1;
			}
		}

		double ratio = countFalling / (double) timeFrame;

		final boolean satisfied = ratio >= fallingFactor ? true : false;
		traceIsSatisfied(index, satisfied);
		return satisfied;
	}
}
