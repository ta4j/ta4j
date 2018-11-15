package org.ta4j.core.cost;

import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;

import java.io.Serializable;


public interface CostModel extends Serializable {

    /**
     * @param trade the trade
     * @param finalIndex final index of consideration for open trades
     * @return Calculates the trading cost of a single trade
     */
    Num calculate(Trade trade, int finalIndex);

    /**
     * @param trade the trade
     * @return Calculates the trading cost of a single trade
     */
    Num calculate(Trade trade);

    /**
     * @param price the price per asset
     * @param amount number of traded assets
     * @return Calculates the trading cost for a certain traded amount
     */
    Num calculate(Num price, Num amount);

    boolean equals(CostModel model);
}