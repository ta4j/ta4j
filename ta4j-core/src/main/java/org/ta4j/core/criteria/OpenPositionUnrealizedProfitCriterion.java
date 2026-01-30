/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.LiveTradingRecord;
import org.ta4j.core.OpenPosition;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Analysis criterion that returns the unrealized profit/loss for the open
 * position.
 *
 * <p>
 * For {@link LiveTradingRecord} it marks the net open position to the series
 * end price and subtracts any remaining entry fees. For other trading records
 * it delegates to {@link Position#getProfit(int, Num)} using the series end
 * price. Returns zero when no open position exists.
 * </p>
 *
 * @since 0.22.2
 */
public class OpenPositionUnrealizedProfitCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        NumFactory factory = series.numFactory();
        if (!position.isOpened()) {
            return factory.zero();
        }
        int endIndex = series.getEndIndex();
        Num closePrice = series.getBar(endIndex).getClosePrice();
        Num profit = position.getProfit(endIndex, closePrice);
        return toSeriesNum(factory, profit);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        NumFactory factory = series.numFactory();
        int endIndex = tradingRecord.getEndIndex(series);
        Num closePrice = series.getBar(endIndex).getClosePrice();
        if (tradingRecord instanceof LiveTradingRecord liveRecord) {
            OpenPosition open = liveRecord.getNetOpenPosition();
            if (open == null || open.amount() == null || open.amount().isZero()) {
                return factory.zero();
            }
            Num amount = toSeriesNum(factory, open.amount());
            Num entryCost = toSeriesNum(factory, open.totalEntryCost());
            Num fees = toSeriesNum(factory, open.totalFees());
            Num currentValue = closePrice.multipliedBy(amount);
            Num gross = open.side() == ExecutionSide.BUY ? currentValue.minus(entryCost)
                    : entryCost.minus(currentValue);
            return gross.minus(fees);
        }
        Position current = tradingRecord.getCurrentPosition();
        if (!current.isOpened()) {
            return factory.zero();
        }
        Num profit = current.getProfit(endIndex, closePrice);
        return toSeriesNum(factory, profit);
    }

    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isGreaterThan(v2);
    }

    private Num toSeriesNum(NumFactory factory, Num value) {
        if (value == null) {
            return factory.zero();
        }
        if (value.isNaN()) {
            return NaN.NaN;
        }
        return factory.numOf(value.getDelegate());
    }
}
