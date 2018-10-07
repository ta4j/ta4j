package org.ta4j.core.trading.rules;

import org.ta4j.core.Bar;
import org.ta4j.core.Order;
import org.ta4j.core.TradingRecord;

import static org.ta4j.core.Order.OrderType;

/**
 * A {@link org.ta4j.core.Rule} which waits for a number of {@link Bar} after an order.
 * </p>
 * Satisfied after a fixed number of bars since the last order.
 */
public class WaitForRule extends AbstractRule {

    /**
     * The type of the order since we have to wait for
     */
    private final OrderType orderType;

    /**
     * The number of bars to wait for
     */
    private final int numberOfBars;

    /**
     * Constructor.
     *
     * @param orderType    the type of the order since we have to wait for
     * @param numberOfBars the number of bars to wait for
     */
    public WaitForRule(OrderType orderType, int numberOfBars) {
        this.orderType = orderType;
        this.numberOfBars = numberOfBars;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history, no need to wait
        if (tradingRecord != null) {
            Order lastOrder = tradingRecord.getLastOrder(orderType);
            if (lastOrder != null) {
                int currentNumberOfBars = index - lastOrder.getIndex();
                satisfied = currentNumberOfBars >= numberOfBars;
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
