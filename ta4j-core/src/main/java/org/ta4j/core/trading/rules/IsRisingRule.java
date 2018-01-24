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
 * Indicator-rising-indicator rule.
 * <p></p>
 * Satisfied when the values of the {@link Indicator indicator} increase
 * within the timeFrame.
 */
public class IsRisingRule extends AbstractRule {

	/** The actual indicator */
	private final Indicator<Decimal> ref;
	/** The timeFrame */
	private final int timeFrame;
	/** The minimum required strenght of the rising */
	private double minStrenght;

	/**
	 * Constructor for strict rising.
	 * 
	 * @param ref the indicator
	 * @param timeFrame the time frame
	 */
	public IsRisingRule(Indicator<Decimal> ref, int timeFrame) {
		this(ref, timeFrame, 1);
	}
	
	/**
	 * Constructor.
	 * 
	 * @param ref the indicator
	 * @param timeFrame the time frame
	 * @param minStrenght the minimum required rising strenght (between '0' and '1', e.g. '1' for strict rising)
	 */
	public IsRisingRule(Indicator<Decimal> ref, int timeFrame, double minStrenght) {
		this.ref = ref;
		this.timeFrame = timeFrame;
		this.minStrenght = minStrenght;
	}

	@Override
	public boolean isSatisfied(int index, TradingRecord tradingRecord) {
		
		if (minStrenght >= 1) {
			minStrenght = 0.99;
		}
		
		int count = 0;
		for (int i = Math.max(0, index - timeFrame + 1); i <= index; i++) {
			if (ref.getValue(i).isGreaterThan(ref.getValue(Math.max(0, i - 1)))) {
				count += 1;
			}
		}

		double ratio = count / (double) timeFrame;
		
		final boolean satisfied = ratio >= minStrenght;
		traceIsSatisfied(index, satisfied);
		return satisfied;
	}
}
