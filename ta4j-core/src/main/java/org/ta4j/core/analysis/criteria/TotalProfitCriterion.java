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
public class TotalProfitCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getTrades().stream()
            .map(trade -> calculateProfit(series, trade))
            .reduce(series.numOf(1), Num::multipliedBy);
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        return calculateProfit(series, trade);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    /**
     * Calculates the profit of a trade (Buy and sell).
     * @param series a time series
     * @param trade a trade
     * @return the profit of the trade
     */
    private Num calculateProfit(TimeSeries series, Trade trade) {
        Num profit = series.numOf(1);
        if (trade.isClosed()) {
            // use price of entry/exit order, if NaN use close price of underlying time series
            Num exitClosePrice = trade.getExit().getPrice().isNaN() ?
                    series.getBar(trade.getExit().getIndex()).getClosePrice() : trade.getExit().getPrice();
            Num entryClosePrice = trade.getEntry().getPrice().isNaN() ?
                    series.getBar(trade.getEntry().getIndex()).getClosePrice() : trade.getEntry().getPrice();



            if (trade.getEntry().isBuy()) {
                profit = exitClosePrice.dividedBy(entryClosePrice);
            } else {
                profit = entryClosePrice.dividedBy(exitClosePrice);
            }
        }
        return profit;
    }
}
