package org.ta4j.core.analysis.criteria;

import org.ta4j.core.*;
import org.ta4j.core.num.Num;

/**
 * Profit and loss criterion.
 * </p>
 * The profit or loss over the provided {@link TimeSeries series}.
 */
public class ProfitLossCriterion extends AbstractBacktestingCriterion {

    public ProfitLossCriterion(PriceType priceType) {
        super(priceType);
    }

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getTrades().stream()
                .filter(trade -> trade.isClosed())
                .map(trade -> calculateProfitLoss(series, trade))
                .reduce(series.numOf(0), (profit1, profit2) -> profit1.plus(profit2));
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        return calculateProfitLoss(series, trade);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    /**
     * Calculates the profit or loss of a sell trade.
     * @param series a time series
     * @param trade a trade
     * @return the profit or loss of the trade
     */
    private Num calculateProfitLoss(TimeSeries series, Trade trade) {
        Num exitPrice = getPrice(series, trade.getExit());
        Num entryPrice = getPrice(series, trade.getEntry());

        return exitPrice.minus(entryPrice).multipliedBy(trade.getExit().getAmount());
    }
}
