/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j;

import eu.verdelhan.ta4j.Order.OrderType;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the full trading record when running a {@link Strategy strategy}.
 */
public class TradingRecord {

    private List<Order> orders = new ArrayList<Order>();
    
    private List<Order> buyOrders = new ArrayList<Order>();
    
    private List<Order> sellOrders = new ArrayList<Order>();
    
    private List<Trade> trades = new ArrayList<Trade>();

    private OrderType startingType;
    
    private Trade currentTrade;

    /**
     * Constructor.
     * @param startingType the starting {@link OrderType order type} of the trading session
     */
    public TradingRecord(OrderType startingType) {
        if (startingType == null) {
            throw new IllegalArgumentException("Starting type must not be null");
        }
        this.startingType = startingType;
        currentTrade = new Trade(startingType);
    }

    public TradingRecord(Order... orders) {
        // TODO add length check
        this(orders[0].getType());
        for (int i = 0; i < orders.length - 1; i += 2) {
            Order o1 = orders[i];
            Order o2 = i+1 < orders.length ? orders[i+1] : null;
            currentTrade = new Trade(o1, o2);
            recordOrder(o1);
            recordOrder(o2);
            recordTrade();
        }
    }
    
    public Trade getCurrentTrade() {
        return currentTrade;
    }
    
    public void operate(int index) {
        operate(index, Decimal.NaN, Decimal.NaN);
    }
    
    public final void operate(int index, Decimal price, Decimal amount) {
        recordOrder(currentTrade.operate(index, price, amount));
        recordTrade();
    }
    
    public boolean isClosed() {
        return !currentTrade.isOpened();
    }
    
    public boolean isEmpty() {
        return trades.isEmpty();
    }
    
    public Trade getTrade(int index) {
        return trades.get(index);
    }
    
    public List<Trade> getTrades() {
        return trades;
    }
    
    public int getTradeCount() {
        return trades.size();
    }
    
    public Order getLastOrder() {
        if (!orders.isEmpty()) {
            return orders.get(orders.size() - 1);
        }
        return null;
    }
    
    public Order getLastOrder(OrderType orderType) {
        if (OrderType.BUY.equals(orderType) && !buyOrders.isEmpty()) {
            return buyOrders.get(buyOrders.size() - 1);
        } else if (OrderType.SELL.equals(orderType) && !sellOrders.isEmpty()) {
            return sellOrders.get(sellOrders.size() - 1);
        }
        return null;
    }
    
    private void recordOrder(Order order) {
        if (order != null) {
            orders.add(order);
            if (OrderType.BUY.equals(order.getType())) {
                buyOrders.add(order);
            } else if (OrderType.SELL.equals(order.getType())) {
                sellOrders.add(order);
            }
        }
    }
    
    private void recordTrade() {
        if (currentTrade.isClosed()) {
            // Adding the trade when closed
            trades.add(currentTrade);
            currentTrade = new Trade(startingType);
        }
    }
}
