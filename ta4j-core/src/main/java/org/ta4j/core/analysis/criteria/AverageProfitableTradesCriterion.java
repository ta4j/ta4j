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

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Average profitable trades criterion.
 * </p>
 * The number of profitable trades.
 */
public class AverageProfitableTradesCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        return isProfitableTrade(series, trade) ? series.numOf(1) : series.numOf(0);
    }

    private boolean isProfitableTrade(TimeSeries series, Trade trade) {
        if (trade.isClosed()) {
            final Num result = calculateResult(series, trade);
            return result.isGreaterThan(series.numOf(1));
        }
        return false;
    }

    private Num calculateResult(TimeSeries series, Trade trade) {
        int entryIndex = trade.getEntry().getIndex();
        int exitIndex = trade.getExit().getIndex();
        if (trade.getEntry().isBuy()) {
            // buy-then-sell trade
            return series.getBar(exitIndex).getClosePrice().dividedBy(series.getBar(entryIndex).getClosePrice());
        } else {
            // sell-then-buy trade
            return series.getBar(entryIndex).getClosePrice().dividedBy(series.getBar(exitIndex).getClosePrice());
        }
    }

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        long numberOfProfitable = tradingRecord.getTrades().stream().filter(t -> isProfitableTrade(series, t)).count();
        return series.numOf(numberOfProfitable).dividedBy(series.numOf(tradingRecord.getTradeCount()));
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
