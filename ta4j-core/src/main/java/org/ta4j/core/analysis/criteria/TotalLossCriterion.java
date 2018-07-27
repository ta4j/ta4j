package org.ta4j.core.analysis.criteria;

import org.ta4j.core.*;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

public class TotalLossCriterion extends AbstractAnalysisCriterion {

    private PriceType priceType;

    public TotalLossCriterion(PriceType priceType) {
        this.priceType = priceType;
    }

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getTrades().stream()
                .filter(trade -> trade.isClosed())
                .filter(trade -> trade.getProfit().isLessThan(PrecisionNum.valueOf(0)))
                .map(trade -> calculateTotalLoss(series, trade))
                .reduce(series.numOf(0), (profit1, profit2) -> profit1.plus(profit2));
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
     * @param series a time series
     * @param trade a trade
     * @return the profit or loss of the trade
     */
    private Num calculateTotalLoss(TimeSeries series, Trade trade) {
        Num exitPrice = getPrice(series, trade.getExit());
        Num entryPrice = getPrice(series, trade.getEntry());

        return exitPrice.minus(entryPrice).multipliedBy(trade.getExit().getAmount());
    }

    private Num getPrice(TimeSeries series, Order order) {
        if (priceType == PriceType.OPEN) {
            return series.getBar(order.getIndex()).getOpenPrice();
        }
        if (priceType == PriceType.HIGH) {
            return series.getBar(order.getIndex()).getMaxPrice();
        }
        if (priceType == PriceType.LOW) {
            return series.getBar(order.getIndex()).getMinPrice();
        }
        return series.getBar(order.getIndex()).getClosePrice();
    }
}
