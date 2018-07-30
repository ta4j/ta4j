/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization 
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core.analysis.criteria;

import org.ta4j.core.*;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

/**
 * Total profit criterion.
 * </p>
 * The total profit of the provided {@link Trade trade(s)} over the provided {@link TimeSeries series}.
 */
public class TotalProfit2Criterion extends AbstractBacktestingCriterion {

    public TotalProfit2Criterion(PriceType priceType) {
        super(priceType);
    }

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getTrades().stream()
                .filter(trade -> trade.isClosed())
                .map(trade -> calculateTotalProfit(series, trade))
                .reduce(series.numOf(0), (profit1, profit2) -> profit1.plus(profit2));
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        return calculateTotalProfit(series, trade);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    /**
     * Calculates the total profit of all the trades
     * @param series a time series
     * @param trade a trade
     * @return the total profit
     */
    private Num calculateTotalProfit(TimeSeries series, Trade trade) {
        Num exitPrice = getPrice(series, trade.getExit());
        Num entryPrice = getPrice(series, trade.getEntry());

        Num profit = exitPrice.minus(entryPrice).multipliedBy(trade.getExit().getAmount());
        if(profit.isGreaterThan(PrecisionNum.valueOf(0))){
            return profit;
        }
        return PrecisionNum.valueOf(0);
    }
}
