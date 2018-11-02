package org.ta4j.core.cost;

import org.ta4j.core.Order;
import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;

public class LinearTransactionCostModel implements CostModel {

    /**
     * Slope of the linear model - fee per trade
     */
    private double feePerTrade;

    /**
     * Constructor.
     * (feePerTrade * x)
     * @param feePerTrade the feePerTrade coefficient (e.g. 0.005 for 0.5% per {@link Order order})
     */
    public LinearTransactionCostModel(double feePerTrade) {
        this.feePerTrade = feePerTrade;
    }

    /**
     * Calculates the transaction cost of a trade.
     * @param trade the trade
     * @param currentIndex current bar index (irrelevant for the LinearTransactionCostModel)
     * @return the absolute order cost
     */
    public Num calculate(Trade trade, int currentIndex) {
        return this.calculate(trade);
    }

    /**
     * Calculates the transaction cost of a trade.
     * @param trade the trade
     * @return the absolute order cost
     */
    public Num calculate(Trade trade) {
        Num totalTradeCost = null;
        Order entryOrder = trade.getEntry();
        if (entryOrder != null) {
            // transaction costs of entry order
            totalTradeCost = entryOrder.getCost();
            if (trade.getExit() != null) {
                totalTradeCost = totalTradeCost.plus(trade.getExit().getCost());
            }
        }
        return totalTradeCost;
    }

    /**
     * @param price execution price
     * @param amount order amount
     * @return the absolute order transaction cost
     */
    public Num calculate(Num price, Num amount) {
        return amount.numOf(feePerTrade).multipliedBy(price).multipliedBy(amount);
    }

    /**
     * Evaluate if two models are equal
     * @param otherModel model to compare with
     */
    public boolean equals(CostModel otherModel) {
        boolean equality = false;
        if (this.getClass().equals(otherModel.getClass())) {
            equality = ((LinearTransactionCostModel) otherModel).feePerTrade == this.feePerTrade;
        }
        return equality;
    }
}
