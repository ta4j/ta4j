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
package eu.verdelhan.ta4j;

import java.util.List;

/**
 * An analysis criterion.
 * <p>
 * Can be used to:
 * <ul>
 * <li>Analyze the performance of a {@link Strategy strategy}
 * <li>Compare several {@link Strategy strategies} together
 * </ul>
 */
public interface AnalysisCriterion {

    /**
     * @param series a time series
     * @param trade a trade
     * @return the criterion value for the trade
     */
    double calculate(TimeSeries series, Trade trade);

    /**
     * @param series a time series
     * @param tradingRecord a trading record
     * @return the criterion value for the trades
     */
    double calculate(TimeSeries series, TradingRecord tradingRecord);

    /**
     * @param manager the time series manager
     * @param strategies a list of strategies
     * @return the best strategy (among the provided ones) according to the criterion
     */
    default Strategy chooseBest(TimeSeriesManager manager, List<Strategy> strategies) {

        Strategy bestStrategy = strategies.get(0);
        double bestCriterionValue = calculate(manager.getTimeSeries(), manager.run(bestStrategy));

        for (int i = 1; i < strategies.size(); i++) {
            Strategy currentStrategy = strategies.get(i);
            double currentCriterionValue = calculate(manager.getTimeSeries(), manager.run(currentStrategy));

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
     * @return true if the first value is better than (according to the criterion) the second one, false otherwise
     */
    boolean betterThan(double criterionValue1, double criterionValue2);
}