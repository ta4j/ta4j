/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
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
package org.ta4j.core

import org.ta4j.core.Trade.TradeType
import org.ta4j.core.num.*

/**
 * An analysis criterion.
 *
 * Can be used to:
 *
 *  * Analyze the performance of a [strategy][Strategy]
 *  * Compare several [strategies][Strategy] together
 *
 */
interface AnalysisCriterion {
    /**
     * @param series   a bar series, not null
     * @param position a position, not null
     * @return the criterion value for the position
     */
    fun calculate(series: BarSeries, position: Position): Num

    /**
     * @param series        a bar series, not null
     * @param tradingRecord a trading record, not null
     * @return the criterion value for the positions
     */
    fun calculate(series: BarSeries, tradingRecord: TradingRecord): Num

    /**
     * @param manager    the bar series manager with entry type of BUY
     * @param strategies a list of strategies
     * @return the best strategy (among the provided ones) according to the
     * criterion
     */
    fun chooseBest(manager: BarSeriesManager, strategies: List<Strategy>): Strategy? {
        return chooseBest(manager, TradeType.BUY, strategies)
    }

    /**
     * @param manager    the bar series manager
     * @param tradeType  the entry type (BUY or SELL) of the first trade in the
     * trading session
     * @param strategies a list of strategies
     * @return the best strategy (among the provided ones) according to the
     * criterion
     */
    fun chooseBest(manager: BarSeriesManager, tradeType: TradeType?, strategies: List<Strategy>): Strategy? {
        var bestStrategy = strategies[0]
        var bestCriterionValue = calculate(manager.barSeries, manager.run(bestStrategy))
        for (i in 1 until strategies.size) {
            val currentStrategy = strategies[i]
            val currentCriterionValue = calculate(manager.barSeries, manager.run(currentStrategy, tradeType))
            if (betterThan(currentCriterionValue, bestCriterionValue)) {
                bestStrategy = currentStrategy
                bestCriterionValue = currentCriterionValue
            }
        }
        return bestStrategy
    }

    /**
     * @param criterionValue1 the first value
     * @param criterionValue2
     *
     *
     * the second value
     * @return true if the first value is better than (according to the criterion)
     * the second one, false otherwise
     */
    fun betterThan(criterionValue1: Num, criterionValue2: Num): Boolean
}