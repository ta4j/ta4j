/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.ExecutionModelSupport.ExecutionTarget;
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
        ExecutionTarget executionTarget = ExecutionModelSupport.resolveExecutionTarget(index, barSeries,
                TradeExecutionModel.PriceSource.NEXT_OPEN);
        if (executionTarget != null) {
            tradingRecord.operate(executionTarget.index(), executionTarget.price(), amount);
        }
    }

}
