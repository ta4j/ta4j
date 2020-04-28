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

import org.ta4j.core.Order.OrderType;
import org.ta4j.core.num.Num;

import java.util.List;

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
     * @param series the bar series, not null
     * @param trade  the trade, not null
     * @return the criterion value for the trade
     */
    Num calculate(BarSeries series, Trade trade);

    /**
     * @param series        the bar series, not null
     * @param tradingRecord the trading record, not null
     * @return the criterion value for the trades
     */
    Num calculate(BarSeries series, TradingRecord tradingRecord);

	/**
	 * @param manager    the bar series manager
	 * @param strategies the list of strategies
	 * @return the best strategy (among the provided ones) according to the
	 *         criterion
	 */
	default Strategy chooseBest(BarSeriesManager manager, List<Strategy> strategies) {
		return chooseBest(manager, strategies, OrderType.BUY);
	}

	/**
	 * @param manager    the bar series manager
	 * @param strategies the list of strategies
	 * @param orderType  the {@link OrderType} used to open the trades
	 * @return the best strategy (among the provided ones) according to the
	 *         criterion
	 */
	default Strategy chooseBest(BarSeriesManager manager, List<Strategy> strategies, OrderType orderType) {
		return chooseBest(manager, strategies, orderType, manager.getBarSeries().getBeginIndex(), manager.getBarSeries().getEndIndex());
	}
	
	/**
	 * @param manager     the bar series manager
	 * @param orderType   the {@link OrderType} used to open the trades
	 * @param strategies  the list of strategies
	 * @param startIndex  the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
	 * @return the best strategy (among the provided ones) according to the
	 *         criterion
	 */
	default Strategy chooseBest(BarSeriesManager manager, List<Strategy> strategies, OrderType orderType, int startIndex, int finishIndex) {

		Strategy bestStrategy = strategies.get(0);
		Num bestCriterionValue = calculate(manager.getBarSeries(), manager.run(bestStrategy, orderType, startIndex, finishIndex));

		for (int i = 1; i < strategies.size(); i++) {
			Strategy currentStrategy = strategies.get(i);
			Num currentCriterionValue = calculate(manager.getBarSeries(), manager.run(currentStrategy, orderType, startIndex, finishIndex));

			if (betterThan(currentCriterionValue, bestCriterionValue)) {
				bestStrategy = currentStrategy;
				bestCriterionValue = currentCriterionValue;
			}
		}

		return bestStrategy;
	}

    /**
     * @param criterionValue1 the first value
     * @param criterionValue2 the second value
     * @return true if the first value is better than (according to the criterion)
     *         the second one, false otherwise
     */
    boolean betterThan(Num criterionValue1, Num criterionValue2);
}
