/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.num.Num;

/**
 * An analysis criterion. It can be used to:
 * 
 * <ul>
 * <li>analyze the performance of a {@link Strategy strategy}
 * <li>compare several {@link Strategy strategies} together
 * </ul>
 */
public interface AnalysisCriterion {

    /** Filter to differentiate between winning or losing positions. */
    public enum PositionFilter {
        /** Consider only winning positions. */
        PROFIT,
        /** Consider only losing positions. */
        LOSS;
    }

    /**
     * @param series   the bar series, not null
     * @param position the position, not null
     * @return the criterion value for the position
     */
    Num calculate(BarSeries series, Position position);

    /**
     * @param series        the bar series, not null
     * @param tradingRecord the trading record, not null
     * @return the criterion value for the positions
     */
    Num calculate(BarSeries series, TradingRecord tradingRecord);

    /**
     * @param manager    the bar series manager with entry type of BUY
     * @param strategies a list of strategies
     * @return the best strategy (among the provided ones) according to the
     *         criterion
     */
    default Strategy chooseBest(BarSeriesManager manager, List<Strategy> strategies) {
        return chooseBest(manager, TradeType.BUY, strategies);
    }

    /**
     * @param manager    the bar series manager
     * @param tradeType  the entry type (BUY or SELL) of the first trade in the
     *                   trading session
     * @param strategies a list of strategies
     * @return the best strategy (among the provided ones) according to the
     *         criterion
     */
    default Strategy chooseBest(BarSeriesManager manager, TradeType tradeType, List<Strategy> strategies) {

        Strategy bestStrategy = strategies.get(0);
        Num bestCriterionValue = calculate(manager.getBarSeries(), manager.run(bestStrategy));

        for (int i = 1; i < strategies.size(); i++) {
            Strategy currentStrategy = strategies.get(i);
            Num currentCriterionValue = calculate(manager.getBarSeries(), manager.run(currentStrategy, tradeType));

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