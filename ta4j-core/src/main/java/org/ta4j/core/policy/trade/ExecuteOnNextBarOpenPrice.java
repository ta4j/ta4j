package org.ta4j.core.policy.trade;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import org.ta4j.core.policy.TradeExecutionPolicy;

/**
 * An execution policy  for {@link BarSeriesManager} objects.
 *
 * Executes trades on the next bar using the opening price
 */
public class ExecuteOnNextBarOpenPrice implements TradeExecutionPolicy {

    @Override
    public void apply(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount) {
        int indexOfExecutedBar = index + 1;
        if(indexOfExecutedBar <= barSeries.getEndIndex()) {
            tradingRecord.operate(indexOfExecutedBar, barSeries.getBar(indexOfExecutedBar).getOpenPrice(), amount);
        }
    }
    
}
