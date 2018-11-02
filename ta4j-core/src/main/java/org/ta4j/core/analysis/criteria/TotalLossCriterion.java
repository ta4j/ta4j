package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

public class TotalLossCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getTrades()
                .stream()
                .filter(Trade::isClosed)
                .map(trade -> calculate(series, trade))
                .reduce(series.numOf(0), Num::plus);
    }

    /**
     * Calculates the gross loss of the given trade
     *
     * @param series a time series
     * @param trade  a trade
     * @return the loss of the trade
     */
    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        if (trade.isClosed()) {
            Num exitPrice = series.getBar(trade.getExit().getIndex()).getClosePrice();
            Num entryPrice = series.getBar(trade.getEntry().getIndex()).getClosePrice();

            Num loss = exitPrice.minus(entryPrice).multipliedBy(trade.getExit().getAmount());
            return loss.isNegative() ? loss : series.numOf(0);

        }
        return series.numOf(0);

    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
