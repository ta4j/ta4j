package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Total profit criterion.
 * </p>
 * The total profit of the provided {@link Trade trade(s)} over the provided {@link TimeSeries series}.
 */
public class TotalProfit2Criterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getTrades().stream()
                .filter(trade -> trade.isClosed())
                .map(trade -> calculateTotalProfit(series, trade))
                .reduce(series.numOf(0), (profit1, profit2) -> profit1.plus(profit2));
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        return calculateTotalProfit(series, trade);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    /**
     * Calculates the total profit of all the trades
     *
     * @param series a time series
     * @param trade  a trade
     * @return the total profit
     */
    private Num calculateTotalProfit(TimeSeries series, Trade trade) {
        Num exitPrice = series.getBar(trade.getExit().getIndex()).getClosePrice();
        Num entryPrice = series.getBar(trade.getEntry().getIndex()).getClosePrice();

        Num profit = exitPrice.minus(entryPrice).multipliedBy(trade.getExit().getAmount());
        if (profit.isGreaterThan(series.numOf(0))) {
            return profit;
        }
        return series.numOf(0);
    }
}
