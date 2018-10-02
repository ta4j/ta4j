package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

public class TotalLossCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getTrades().stream().filter(Trade::isClosed).map(trade -> calculateTotalLoss(series, trade)).reduce(series.numOf(0), Num::plus);
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        return calculateTotalLoss(series, trade);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    /**
     * Calculates the total loss of all the trades
     *
     * @param series a time series
     * @param trade  a trade
     * @return the profit or loss of the trade
     */
    private Num calculateTotalLoss(TimeSeries series, Trade trade) {
        Num exitPrice = series.getBar(trade.getExit().getIndex()).getClosePrice();
        Num entryPrice = series.getBar(trade.getEntry().getIndex()).getClosePrice();

        Num loss = exitPrice.minus(entryPrice).multipliedBy(trade.getExit().getAmount());
        if (loss.isLessThan(series.numOf(0))) {
            return loss;
        }
        return series.numOf(0);
    }
}
