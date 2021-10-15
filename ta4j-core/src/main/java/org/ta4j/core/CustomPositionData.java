package org.ta4j.core;

import org.ta4j.core.num.Num;

/**
 * Extension point that enable a {@link Position} to carry additional context data.
 *
 * This class be used as is or extended by developers and enables the development of dynamic
 * rules.
 *
 * See usage example of this class in {@link org.ta4j.core.rules.FixedPriceStopLossRule}
 * or {@link org.ta4j.core.rules.FixedPriceStopGainRule}.
 */
public class CustomPositionData {

    /** position's current stop loss price. */
    private Num stopLossPrice;

    /** position's take profit price. */
    private Num takeProfitPrice;

    /**
     * Default constructor.
     */
    public CustomPositionData() {
    }

    /**
     * Constructor accpeting the initial stop loss and take profit prices.
     * @param stopLossPrice position's current stop loss price.
     * @param takeProfitPrice position's take profit price.
     */
    public CustomPositionData(Num stopLossPrice, Num takeProfitPrice) {
        this.stopLossPrice = stopLossPrice;
        this.takeProfitPrice = takeProfitPrice;
    }

    /**
     * Returns position's  current stop loss price.
     * @return current stop loss price if present, null otherwise.
     */
    public Num getStopLossPrice() {
        return stopLossPrice;
    }

    /**
     * Updates position's current stop loss price.
     * @param stopLossPrice the new stop loss price.
     */
    public void setStopLossPrice(Num stopLossPrice) {
        this.stopLossPrice = stopLossPrice;
    }

    /**
     * Returns position's initial take profit price.
     * @return the initial take prifit price if present, null otherwise.
     */
    public Num getTakeProfitPrice() {
        return takeProfitPrice;
    }

    /**
     * Updates position's current take profit price.
     * @param takeProfitPrice the new take profit price.
     */
    public void setTakeProfitPrice(Num takeProfitPrice) {
        this.takeProfitPrice = takeProfitPrice;
    }
}
