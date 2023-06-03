package org.ta4j.core.policy.trade;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import org.ta4j.core.policy.TradeExecutionPolicy;

/**
 * An execution policy  for {@link BarSeriesManager} objects.
 *
 * Executes trades on the current bar using the closing price
 */
public class ExecuteOnClosingPrice implements TradeExecutionPolicy {

    @Override
    public void apply(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
        tradingRecord.operate(index, barSeries.getBar(index).getClosePrice(), amount);
    }

}
