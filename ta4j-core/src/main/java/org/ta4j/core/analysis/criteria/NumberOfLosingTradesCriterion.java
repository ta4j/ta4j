package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Number of losing trades criterion.
 */
public class NumberOfLosingTradesCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        long numberOfLosingTrades = tradingRecord.getTrades()
                .stream()
                .filter(Trade::isClosed)
                .filter(trade -> isLosingTrade(series, trade))
                .count();
        return series.numOf(numberOfLosingTrades);
    }

    private boolean isLosingTrade(TimeSeries series, Trade trade) {
        if (trade.isClosed()) {
            Num exitPrice = series.getBar(trade.getExit().getIndex()).getClosePrice();
            Num entryPrice = series.getBar(trade.getEntry().getIndex()).getClosePrice();

            Num profit = exitPrice.minus(entryPrice).multipliedBy(trade.getExit().getAmount());
            return profit.isNegative();
        }
        return false;
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        return isLosingTrade(series, trade) ? series.numOf(1) : series.numOf(0);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }
}
