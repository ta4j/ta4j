package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Gross profit criterion.
 * </p>
 * The gross profit of the provided {@link Trade trade(s)} over the provided {@link TimeSeries series}.
 */
public class TotalProfit2Criterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getTrades().stream().filter(Trade::isClosed).map(trade -> calculate(series, trade)).reduce(series.numOf(0), Num::plus);
    }

    /**
     * Calculates the gross profit value of given trade
     *
     * @param series a time series
     * @param trade  a trade to calculate profit
     * @return the total profit
     */
    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        if (trade.isClosed()) {
            Num exitPrice = series.getBar(trade.getExit().getIndex()).getClosePrice();
            Num entryPrice = series.getBar(trade.getEntry().getIndex()).getClosePrice();

            Num profit = exitPrice.minus(entryPrice).multipliedBy(trade.getExit().getAmount());
            return profit.isPositive() ? profit : series.numOf(0);
        }
        return series.numOf(0);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }


}
