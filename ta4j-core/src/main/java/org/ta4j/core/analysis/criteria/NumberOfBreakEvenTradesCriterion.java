package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

/**
 * Number of break even trades criterion.
 */
public class NumberOfBreakEvenTradesCriterion extends AbstractAnalysisCriterion {

   @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
         long numberOfLosingTrades = tradingRecord.getTrades().stream()
                .filter(trade -> trade.isClosed())
                .filter(trade -> isBreakEvenTrade(series, trade)).count();
         return PrecisionNum.valueOf(numberOfLosingTrades);
    }

    private boolean isBreakEvenTrade(TimeSeries series, Trade trade) {
        Num exitPrice = series.getBar(trade.getExit().getIndex()).getClosePrice();
        Num entryPrice = series.getBar(trade.getEntry().getIndex()).getClosePrice();

        Num profit = exitPrice.minus(entryPrice).multipliedBy(trade.getExit().getAmount());
        return profit.isEqual(PrecisionNum.valueOf(0));
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        return isBreakEvenTrade(series, trade) ? PrecisionNum.valueOf(1) : PrecisionNum.valueOf(0);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }
}
