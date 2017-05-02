/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package eu.verdelhan.ta4j.trading.rules;

import eu.verdelhan.ta4j.Order;
import static eu.verdelhan.ta4j.Order.OrderType;
import eu.verdelhan.ta4j.TradingRecord;

/**
 * A {@link Rule rule} which waits for a number of {@link Tick ticks} after an order.
 * <p>
 * Satisfied after a fixed number of ticks since the last order.
 */
public class WaitForRule extends AbstractRule {

    /** The type of the order since we have to wait for */
    private OrderType orderType;
    
    /** The number of ticks to wait for */
    private int numberOfTicks;

    /**
     * Constructor.
     * @param orderType the type of the order since we have to wait for
     * @param numberOfTicks the number of ticks to wait for
     */
    public WaitForRule(OrderType orderType, int numberOfTicks) {
        this.orderType = orderType;
        this.numberOfTicks = numberOfTicks;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history, no need to wait
        if (tradingRecord != null) {
            Order lastOrder = tradingRecord.getLastOrder(orderType);
            if (lastOrder != null) {
                int currentNumberOfTicks = index - lastOrder.getIndex();
                satisfied = currentNumberOfTicks >= numberOfTicks;
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
