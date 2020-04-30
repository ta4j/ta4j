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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Order.OrderType;
import org.ta4j.core.num.Num;

/**
 * This class enables backtesting of multiple strategies and comparing them to
 * see which is the best.
 * 
 * <p>
 * The backtesting can be done in one of the following ways:
 * 
 * <ul>
 * <li>finds the best strategy (among the provided ones)
 * <li>finds the Top 10 best strategies (among the provided ones)
 * <li>finds the best strategy and compares the calculated criterion value with
 * a required criterion value
 * </ul>
 */
public class BacktestExecutor {

	/**
	 * Constructor.
	 *
	 * @param criterion the criterion to execute
	 */
	public BacktestExecutor(final AnalysisCriterion criterion) {
		this.criterion = criterion;
	}

	private final AnalysisCriterion criterion;

	/**
	 * @param manager    the bar series manager
	 * @param strategies a list of strategies
	 * @return the result with the best strategy (among the provided ones) according
	 *         to the criterion
	 */
	public BacktestResult chooseBest(BarSeriesManager manager, List<Strategy> strategies) {
		return chooseBest(manager, strategies, OrderType.BUY, manager.getBarSeries().getBeginIndex(),
				manager.getBarSeries().getEndIndex());
	}

	/**
	 * @param manager    the bar series manager
	 * @param strategies a list of strategies
	 * @param orderType  the {@link OrderType} used to open the trades
	 * @param pastBars   the number of past bars to go backward (included)
	 * @return the result with the best strategy (among the provided ones) according
	 *         to the criterion
	 */
	public BacktestResult chooseBest(
			BarSeriesManager manager,
			List<Strategy> strategies,
			OrderType orderType,
			int pastBars) {
		return chooseBest(manager, strategies, orderType, Math.max(0, manager.getBarSeries().getEndIndex() - pastBars),
				manager.getBarSeries().getEndIndex());
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
	public BacktestResult chooseBest(
			BarSeriesManager manager,
			List<Strategy> strategies,
			OrderType orderType,
			int startIndex,
			int finishIndex) {
		Strategy bestStrategy = strategies.get(0);
		Num bestCriterionValue = criterion.calculate(manager.getBarSeries(),
				manager.run(bestStrategy, orderType, startIndex, finishIndex));

		for (int i = 1; i < strategies.size(); i++) {
			Strategy currentStrategy = strategies.get(i);
			TradingRecord currentTradingRecord = manager.run(currentStrategy, orderType, startIndex, finishIndex);
			Num currentCriterionValue = criterion.calculate(manager.getBarSeries(), currentTradingRecord);

			if (criterion.betterThan(currentCriterionValue, bestCriterionValue)) {
				bestStrategy = currentStrategy;
				bestCriterionValue = currentCriterionValue;
			}
		}

		return new BacktestResult(bestStrategy, bestCriterionValue);
	}

	/**
	 * @param manager    the bar series manager
	 * @param strategies a list of strategies
	 * @param topNumbers the top number of returned backtest result
	 * @return the top results with the best strategies (among the provided ones)
	 *         according to the criterion
	 */
	public List<BacktestResult> chooseBest(BarSeriesManager manager, List<Strategy> strategies, int topNumbers) {
		return chooseBest(manager, strategies, topNumbers, OrderType.BUY, manager.getBarSeries().getBeginIndex(),
				manager.getBarSeries().getEndIndex());
	}

	/**
	 * @param manager    the bar series manager
	 * @param strategies a list of strategies
	 * @param topNumbers the top number of returned backtest result
	 * @param orderType  the {@link OrderType} used to open the trades
	 * @param pastBars   the number of past bars to go backward (included)
	 * @return the top results with the best strategies (among the provided ones)
	 *         according to the criterion
	 */
	public List<BacktestResult> chooseBest(
			BarSeriesManager manager,
			List<Strategy> strategies,
			int topNumbers,
			OrderType orderType,
			int pastBars) {
		return chooseBest(manager, strategies, topNumbers, orderType,
				Math.max(0, manager.getBarSeries().getEndIndex() - pastBars), manager.getBarSeries().getEndIndex());
	}

	/**
	 * @param manager     the bar series manager
	 * @param strategies  a list of strategies
	 * @param topNumbers  the top number of returned backtest result
	 * @param orderType   the {@link OrderType} used to open the trades
	 * @param startIndex  the start index for the run (included)
	 * @param finishIndex the finish index for the run (included)
	 * @return the top results with the best strategies (among the provided ones)
	 *         according to the criterion
	 */
	public List<BacktestResult> chooseBest(
			BarSeriesManager manager,
			List<Strategy> strategies,
			int topNumbers,
			OrderType orderType,
			int startIndex,
			int finishIndex) {

		List<BacktestResult> bestStrategies = new ArrayList<>();

		for (int i = 0; i < strategies.size(); i++) {
			Strategy currentStrategy = strategies.get(i);
			Num currentCriterionValue = criterion.calculate(manager.getBarSeries(),
					manager.run(currentStrategy, orderType, startIndex, finishIndex));
			bestStrategies.add(new BacktestResult(currentStrategy, currentCriterionValue));
		}

		boolean isBetterIfHigher = criterion.betterThan(manager.getBarSeries().numOf(2),
				manager.getBarSeries().numOf(1));

		return bestStrategies.stream()
				.sorted(isBetterIfHigher ? BacktestResult.COMPARE.reversed() : BacktestResult.COMPARE)
				.limit(topNumbers)
				.collect(Collectors.toList());
	}

	/**
	 * @param manager                the bar series manager
	 * @param strategies             a list of strategies
	 * @param requiredCriterionValue the required criterion value tested against the
	 *                               best available criterion value
	 * @return the result with the best strategy (among the provided ones) according
	 *         to the criterion
	 */
	public BacktestResult chooseBest(BarSeriesManager manager, List<Strategy> strategies, Num requiredCriterionValue) {
		return chooseBest(manager, strategies, requiredCriterionValue, OrderType.BUY,
				manager.getBarSeries().getBeginIndex(), manager.getBarSeries().getEndIndex());
	}

	/**
	 * @param manager                the bar series manager
	 * @param strategies             a list of strategies
	 * @param requiredCriterionValue the required criterion value tested against the
	 *                               best available criterion value
	 * @param orderType              the {@link OrderType} used to open the trades
	 * @param pastBars               the number of past bars to go backward
	 *                               (included)
	 * @return the result with the best strategy (among the provided ones) according
	 *         to the criterion
	 */
	public BacktestResult chooseBest(
			BarSeriesManager manager,
			List<Strategy> strategies,
			Num requiredCriterionValue,
			OrderType orderType,
			int pastBars) {
		return chooseBest(manager, strategies, requiredCriterionValue, orderType,
				Math.max(0, manager.getBarSeries().getEndIndex() - pastBars), manager.getBarSeries().getEndIndex());
	}

	/**
	 * @param manager                the bar series manager
	 * @param strategies             a list of strategies
	 * @param requiredCriterionValue the required criterion value tested against the
	 *                               best available criterion value
	 * @param orderType              the {@link OrderType} used to open the trades
	 * @param startIndex             the start index for the run (included)
	 * @param finishIndex            the finish index for the run (included)
	 * @return the result with the best strategy (among the provided ones) according
	 *         to the criterion
	 */
	public BacktestResult chooseBest(
			BarSeriesManager manager,
			List<Strategy> strategies,
			Num requiredCriterionValue,
			OrderType orderType,
			int startIndex,
			int finishIndex) {
		BacktestResult bestResult = chooseBest(manager, strategies, orderType, startIndex, finishIndex);
		Num bestCriterionValue = bestResult.getCalculatedNum();

		boolean isAccepted = criterion.betterThan(bestCriterionValue, requiredCriterionValue)
				|| bestCriterionValue.isEqual(requiredCriterionValue);

		return new BacktestResult(bestResult.getStrategy(), requiredCriterionValue, bestCriterionValue, isAccepted);
	}

}
