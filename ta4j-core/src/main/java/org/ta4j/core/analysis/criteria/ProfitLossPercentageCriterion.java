package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Profit and loss in percentage criterion.
 * </p>
 * The profit or loss in percentage over the provided {@link TimeSeries series}.
 * https://www.investopedia.com/ask/answers/how-do-you-calculate-percentage-gain-or-loss-investment/
 */
public class ProfitLossPercentageCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getTrades().stream()
                .filter(trade -> trade.isClosed())
                .map(trade -> calculateProfitLossInPercentage(series, trade))
                .reduce(series.numOf(0), (profit1, profit2) -> profit1.plus(profit2));
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        return calculateProfitLossInPercentage(series, trade);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    /**
     * Calculates the profit or loss of a sell trade in percentage.
     * @param series a time series
     * @param trade a trade
     * @return the profit or loss of the trade
     */
    private Num calculateProfitLossInPercentage(TimeSeries series, Trade trade) {
        Num pricePurchase = series.getBar(trade.getEntry().getIndex()).getClosePrice();
        Num priceSold = series.getBar(trade.getExit().getIndex()).getClosePrice();

        return priceSold.minus(pricePurchase).dividedBy(pricePurchase).multipliedBy(series.numOf(100));
    }
}