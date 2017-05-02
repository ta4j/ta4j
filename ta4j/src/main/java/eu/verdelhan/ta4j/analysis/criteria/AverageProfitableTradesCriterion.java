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
package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.TradingRecord;

/**
 * Average profitable trades criterion.
 * <p>
 * The number of profitable trades.
 */
public class AverageProfitableTradesCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        int entryIndex = trade.getEntry().getIndex();
        int exitIndex = trade.getExit().getIndex();

        Decimal result;
        if (trade.getEntry().isBuy()) {
            // buy-then-sell trade
            result = series.getTick(exitIndex).getClosePrice().dividedBy(series.getTick(entryIndex).getClosePrice());
        } else {
            // sell-then-buy trade
            result = series.getTick(entryIndex).getClosePrice().dividedBy(series.getTick(exitIndex).getClosePrice());
        }

        return (result.isGreaterThan(Decimal.ONE)) ? 1d : 0d;
    }

    @Override
    public double calculate(TimeSeries series, TradingRecord tradingRecord) {
        int numberOfProfitable = 0;
        for (Trade trade : tradingRecord.getTrades()) {
            int entryIndex = trade.getEntry().getIndex();
            int exitIndex = trade.getExit().getIndex();

            Decimal result;
            if (trade.getEntry().isBuy()) {
                // buy-then-sell trade
                result = series.getTick(exitIndex).getClosePrice().dividedBy(series.getTick(entryIndex).getClosePrice());
            } else {
                // sell-then-buy trade
                result = series.getTick(entryIndex).getClosePrice().dividedBy(series.getTick(exitIndex).getClosePrice());
            }
            if (result.isGreaterThan(Decimal.ONE)) {
                numberOfProfitable++;
            }
        }
        return ((double) numberOfProfitable) / tradingRecord.getTradeCount();
    }

    @Override
    public boolean betterThan(double criterionValue1, double criterionValue2) {
        return criterionValue1 > criterionValue2;
    }
}
