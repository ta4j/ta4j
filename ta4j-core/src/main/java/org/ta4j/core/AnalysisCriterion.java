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
package org.ta4j.core;

import java.util.List;

import org.ta4j.core.Order.OrderType;
import org.ta4j.core.backtest.BacktestExecutor;
import org.ta4j.core.backtest.BacktestResult;
import org.ta4j.core.num.Num;

/**
 * An analysis criterion.
 *
 * Can be used to:
 * <ul>
 * <li>Analyze the performance of a {@link Strategy strategy}
 * <li>Compare several {@link Strategy strategies} together
 * </ul>
 */
public interface AnalysisCriterion {

	/**
	 * @param series a bar series, not null
	 * @param trade  a trade, not null
	 * @return the criterion value for the trade
	 */
	Num calculate(BarSeries series, Trade trade);

	/**
	 * @param series        a bar series, not null
	 * @param tradingRecord a trading record, not null
	 * @return the criterion value for the trades
	 */
	Num calculate(BarSeries series, TradingRecord tradingRecord);

	/**
	 * @param manager    the bar series manager
	 * @param strategies a list of strategies
	 * @return the best strategy (among the provided ones) according to the
	 *         criterion
	 */
	default Strategy chooseBest(BarSeriesManager manager, List<Strategy> strategies) {
		return new BacktestExecutor(this).chooseBest(manager, strategies).getStrategy();
	}

	/**
	 * @param manager    the bar series manager
	 * @param strategies a list of strategies
	 * @return the top list of AnalysisResult with the best strategies (among the
	 *         provided ones) according to the criterion
	 */
	default List<BacktestResult> chooseBest(BarSeriesManager manager, List<Strategy> strategies, int topNumbers) {
		return new BacktestExecutor(this).chooseBest(manager, strategies, topNumbers);
	}
	
	/**
	 * @param manager     the bar series manager
	 * @param strategies  a list of strategies
	 * @param orderType   the {@link OrderType} used to open the trades
	 * @param startIndex  the start index for the run (included)
	 * @param finishIndex the finish index for the run (included)
	 * @return the result with the best strategy (among the provided ones) according
	 *         to the criterion
	 */
	default BacktestResult chooseBest(BarSeriesManager manager, List<Strategy> strategies, OrderType orderType, int startIndex, int finishIndex) {
		return new BacktestExecutor(this).chooseBest(manager, strategies, orderType, startIndex, finishIndex);
	}
	
	/**
	 * @param manager                the bar series manager
	 * @param strategies             a list of strategies
	 * @param requiredCriterionValue the required criterion value needed tested
	 *                               against the best available criterion value
	 * @return the AnalysisResult with the best strategy (among the provided ones)
	 *         according to the criterion
	 */
	default BacktestResult chooseBest(BarSeriesManager manager, List<Strategy> strategies, Num requiredCriterionValue) {
		return new BacktestExecutor(this).chooseBest(manager, strategies, requiredCriterionValue);
	}

	/**
	 * @param criterionValue1 the first value
	 * @param criterionValue2 the second value
	 * @return true if the first value is better than (according to the criterion)
	 *         the second one, false otherwise
	 */
	boolean betterThan(Num criterionValue1, Num criterionValue2);
}
