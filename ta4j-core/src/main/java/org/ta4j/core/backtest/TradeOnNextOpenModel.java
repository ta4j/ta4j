/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * An execution model for {@link BarSeriesManager} objects.
 *
 * Executes trades on the next bar at the open price.
 *
 * This is used for strategies that explicitly trade just after a new bar opens
 * at bar index `t + 1`, in order to execute new or close existing trades as
 * close as possible to the opening price.
 */
public class TradeOnNextOpenModel implements TradeExecutionModel {

    @Override
    public void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
        int indexOfExecutedBar = index + 1;
        if (indexOfExecutedBar <= barSeries.getEndIndex()) {
            tradingRecord.operate(indexOfExecutedBar, barSeries.getBar(indexOfExecutedBar).getOpenPrice(), amount);
        }
    }

}
