package org.ta4j.core.cost;

import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;


public class ZeroCostModel implements CostModel {

    /**
     * Constructor for a trading cost-free model.
     *
     */
    public ZeroCostModel() {}

    public Num calculate(Trade trade) {
        return calculate(trade, 0);
    }

    public Num calculate(Trade trade, int currentIndex) {
        return trade.getEntry().getPricePerAsset().numOf(0);
    }

    public Num calculate(Num price, Num amount) {
        return price.numOf(0);
    }

    /**
     * Evaluate if two models are equal
     * @param otherModel model to compare with
     */
    public boolean equals(CostModel otherModel) {
        boolean equality = false;
        if (this.getClass().equals(otherModel.getClass())) {
            equality = true;
        }
        return equality;
    }
}
