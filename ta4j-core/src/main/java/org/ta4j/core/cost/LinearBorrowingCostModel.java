package org.ta4j.core.cost;

import org.ta4j.core.Order;
import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;

public class LinearBorrowingCostModel implements CostModel {

    /**
     * Slope of the linear model - fee per period
     */
    private double feePerPeriod;

    /**
     * Constructor.
     * (feePerPeriod * nPeriod)
     * @param feePerPeriod the coefficient (e.g. 0.0001 for 1bp per period)
     */
    public LinearBorrowingCostModel(double feePerPeriod) {
        this.feePerPeriod = feePerPeriod;
    }

    public Num calculate(Num price, Num amount) {
        // borrowing costs depend on borrowed period
        return price.numOf(0);
    }

    /**
     * Calculates the borrowing cost of a closed trade.
     * @param trade the trade
     * @return the absolute order cost
     */
    public Num calculate(Trade trade) {
        if (trade.isOpened()) { throw new IllegalArgumentException("Trade is not closed. Final index of observation needs to be provided."); }
        return calculate(trade, trade.getExit().getIndex());
    }

    /**
     * Calculates the borrowing cost of a trade.
     * @param trade the trade
     * @param currentIndex final bar index to be considered (for open trades)
     * @return the absolute order cost
     */
    public Num calculate(Trade trade, int currentIndex) {
        Order entryOrder = trade.getEntry();
        Order exitOrder = trade.getExit();
        Num borrowingCost = trade.getEntry().getNetPrice().numOf(0);

        // borrowing costs apply for short positions only
        if (entryOrder != null && entryOrder.getType().equals(Order.OrderType.SELL) && entryOrder.getAmount() != null) {
            int tradingPeriods = 0;
            if (trade.isClosed()) {
                tradingPeriods = exitOrder.getIndex() - entryOrder.getIndex();
            } else if (trade.isOpened()) {
                tradingPeriods = currentIndex - entryOrder.getIndex();
            }
            borrowingCost = getHoldingCostForPeriods(tradingPeriods, trade.getEntry().getValue());
        }
        return borrowingCost;
    }

    /**
     * @param tradingPeriods number of periods
     * @param tradedValue value of the trade initial trade position
     * @return the absolute borrowing cost
     */
    private Num getHoldingCostForPeriods(int tradingPeriods, Num tradedValue) {
        return tradedValue
                .multipliedBy(tradedValue.numOf(tradingPeriods)
                        .multipliedBy(tradedValue.numOf(feePerPeriod)));
    }

    /**
     * Evaluate if two models are equal
     * @param otherModel model to compare with
     */
    public boolean equals(CostModel otherModel) {
        boolean equality = false;
        if (this.getClass().equals(otherModel.getClass())) {
            equality = ((LinearBorrowingCostModel) otherModel).feePerPeriod == this.feePerPeriod;
        }
        return equality;
    }
}
