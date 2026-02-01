/*
 * SPDX-License-Identifier: MIT
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