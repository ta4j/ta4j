/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * An execution model for {@link BarSeriesManager} objects.
 *
 * Executes trades on the current bar being considered using the closing price.
 *
 * This is used for strategies that explicitly trade just before the bar closes
 * at index `t`, in order to execute new or close existing trades as close as
 * possible to the closing price.
 */
public class TradeOnCurrentCloseModel implements TradeExecutionModel {

    @Override
    public void execute(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
        tradingRecord.operate(index, barSeries.getBar(index).getClosePrice(), amount);
    }

}
