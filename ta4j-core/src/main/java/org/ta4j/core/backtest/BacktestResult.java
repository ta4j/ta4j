/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2020 Ta4j Organization & respective
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
package org.ta4j.core.backtest;

import java.util.Comparator;

import org.ta4j.core.Strategy;
import org.ta4j.core.num.Num;

/**
 * This class holds the backtest result of the AnalysisCriterion
 * running against a Strategy within a BarSeries.
 */
public class BacktestResult {

	static final Comparator<BacktestResult> COMPARE = Comparator
			.nullsLast(Comparator.comparing(BacktestResult::getCalculatedNum));

	/**
	 * Constructor.
	 * 
	 * @param strategy      the strategy
	 * @param calculatedNum the associated criterion value
	 */
	public BacktestResult(Strategy strategy, Num calculatedNum) {
		this.strategy = strategy;
		this.calculatedNum = calculatedNum;
		this.accepted = null;
		this.requiredNum = null;
	}

	/**
	 * Constructor.
	 * 
	 * @param strategy      the strategy
	 * @param requiredNum   the required criterion value
	 * @param calculatedNum the calculated criterion value
	 * @param accepted      the acceptance of the criterion tested against a
	 *                      required criterion value
	 */
	public BacktestResult(Strategy strategy, Num requiredNum, Num calculatedNum, Boolean accepted) {
		this.strategy = strategy;
		this.requiredNum = requiredNum;
		this.calculatedNum = calculatedNum;
		this.accepted = accepted;
	}

	private final Strategy strategy;
	private final Num requiredNum;
	private final Num calculatedNum;
	private final Boolean accepted;

	/** @return the Strategy */
	public Strategy getStrategy() {
		return strategy;
	}

	/** @return the calculated value of the criterion */
	public Num getCalculatedNum() {
		return calculatedNum;
	}

	/**
	 * @return the required value of the criterion (is null if no required
	 *         criterion value was set)
	 */
	public Num getRequiredNum() {
		return requiredNum;
	}

	/**
	 * @return the acceptance of the criterion tested against a required criterion
	 *         value (is null if no required criterion value was set)
	 */
	public Boolean getAccepted() {
		return accepted;
	}

}
