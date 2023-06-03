package org.ta4j.core.policy;

import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import org.ta4j.core.policy.trade.ExecuteOnNextBarOpenPrice;

/**
 * An execution policy  for {@link BarSeriesManager} objects.
 *
 * Used for backtesting. Instructs {@link BarSeriesManager} on how to execute trades
 */
public interface TradeExecutionPolicy {

    void apply(int index, TradingRecord tradingRecord, BarSeries barSeries, Num amount);

    public static TradeExecutionPolicy getDefault() {
        return new ExecuteOnNextBarOpenPrice();
    }

}
