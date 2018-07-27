package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

/**
 * Number of losing trades criterion.
 */
public class NumberOfLosingTradesCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
         long numberOfLosingTrades = tradingRecord.getTrades().stream()
                .filter(trade -> trade.isClosed())
                .filter(trade -> trade.getProfit().isLessThan(PrecisionNum.valueOf(0))).count();
         return PrecisionNum.valueOf(numberOfLosingTrades);
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        return series.numOf(1);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }
}
