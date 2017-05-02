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
package eu.verdelhan.ta4j;

import eu.verdelhan.ta4j.Order.OrderType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A history/record of a trading session.
 * <p>
 * Holds the full trading record when running a {@link Strategy strategy}.
 * It is used to:
 * <ul>
 * <li>check to satisfaction of some trading rules (when running a strategy)
 * <li>analyze the performance of a trading strategy
 * </ul>
 */
public class TradingRecord implements Serializable {

	private static final long serialVersionUID = -4436851731855891220L;

	/** The recorded orders */
    private List<Order> orders = new ArrayList<Order>();
    
    /** The recorded BUY orders */
    private List<Order> buyOrders = new ArrayList<Order>();
    
    /** The recorded SELL orders */
    private List<Order> sellOrders = new ArrayList<Order>();
    
    /** The recorded entry orders */
    private List<Order> entryOrders = new ArrayList<Order>();
    
    /** The recorded exit orders */
    private List<Order> exitOrders = new ArrayList<Order>();
    
    /** The recorded trades */
    private List<Trade> trades = new ArrayList<Trade>();

    /** The entry type (BUY or SELL) in the trading session */
    private OrderType startingType;
    
    /** The current non-closed trade (there's always one) */
    private Trade currentTrade;

    /**
     * Constructor.
     */
    public TradingRecord() {
        this(OrderType.BUY);
    }
    
    /**
     * Constructor.
     * @param entryOrderType the {@link OrderType order type} of entries in the trading session
     */
    public TradingRecord(OrderType entryOrderType) {
        if (entryOrderType == null) {
            throw new IllegalArgumentException("Starting type must not be null");
        }
        this.startingType = entryOrderType;
        currentTrade = new Trade(entryOrderType);
    }

    /**
     * Constructor.
     * @param orders the orders to be recorded (cannot be empty)
     */
    public TradingRecord(Order... orders) {
        this(orders[0].getType());
        for (Order o : orders) {
            boolean newOrderWillBeAnEntry = currentTrade.isNew();
            if (newOrderWillBeAnEntry && o.getType() != startingType) {
                // Special case for entry/exit types reversal
                // E.g.: BUY, SELL,
                //    BUY, SELL,
                //    SELL, BUY,
                //    BUY, SELL
                currentTrade = new Trade(o.getType());
            }
            Order newOrder = currentTrade.operate(o.getIndex(), o.getPrice(), o.getAmount());
            recordOrder(newOrder, newOrderWillBeAnEntry);
        }
    }
    
    /**
     * @return the current trade
     */
    public Trade getCurrentTrade() {
        return currentTrade;
    }
    
    /**
     * Operates an order in the trading record.
     * @param index the index to operate the order
     */
    public final void operate(int index) {
        operate(index, Decimal.NaN, Decimal.NaN);
    }
    
    /**
     * Operates an order in the trading record.
     * @param index the index to operate the order
     * @param price the price of the order
     * @param amount the amount to be ordered
     */
    public final void operate(int index, Decimal price, Decimal amount) {
        if (currentTrade.isClosed()) {
            // Current trade closed, should not occur
            throw new IllegalStateException("Current trade should not be closed");
        }
        boolean newOrderWillBeAnEntry = currentTrade.isNew();
        Order newOrder = currentTrade.operate(index, price, amount);
        recordOrder(newOrder, newOrderWillBeAnEntry);
    }
    
    /**
     * Operates an entry order in the trading record.
     * @param index the index to operate the entry
     * @return true if the entry has been operated, false otherwise
     */
    public final boolean enter(int index) {
        return enter(index, Decimal.NaN, Decimal.NaN);
    }
    
    /**
     * Operates an entry order in the trading record.
     * @param index the index to operate the entry
     * @param price the price of the order
     * @param amount the amount to be ordered
     * @return true if the entry has been operated, false otherwise
     */
    public final boolean enter(int index, Decimal price, Decimal amount) {
        if (currentTrade.isNew()) {
            operate(index, price, amount);
            return true;
        }
        return false;
    }
    
    /**
     * Operates an exit order in the trading record.
     * @param index the index to operate the exit
     * @return true if the exit has been operated, false otherwise
     */
    public final boolean exit(int index) {
        return exit(index, Decimal.NaN, Decimal.NaN);
    }
    
    /**
     * Operates an exit order in the trading record.
     * @param index the index to operate the exit
     * @param price the price of the order
     * @param amount the amount to be ordered
     * @return true if the exit has been operated, false otherwise
     */
    public final boolean exit(int index, Decimal price, Decimal amount) {
        if (currentTrade.isOpened()) {
            operate(index, price, amount);
            return true;
        }
        return false;
    }
    
    /**
     * @return true if no trade is open, false otherwise
     */
    public boolean isClosed() {
        return !currentTrade.isOpened();
    }
    
    /**
     * @return the recorded trades
     */
    public List<Trade> getTrades() {
        return trades;
    }
    
    /**
     * @return the number of recorded trades
     */
    public int getTradeCount() {
        return trades.size();
    }
    
    /**
     * @return the last trade recorded
     */
    public Trade getLastTrade() {
        if (!trades.isEmpty()) {
            return trades.get(trades.size() - 1);
        }
        return null;
    }
    
    /**
     * @return the last order recorded
     */
    public Order getLastOrder() {
        if (!orders.isEmpty()) {
            return orders.get(orders.size() - 1);
        }
        return null;
    }
    
    /**
     * @param orderType the type of the order to get the last of
     * @return the last order (of the provided type) recorded
     */
    public Order getLastOrder(OrderType orderType) {
        if (OrderType.BUY.equals(orderType) && !buyOrders.isEmpty()) {
            return buyOrders.get(buyOrders.size() - 1);
        } else if (OrderType.SELL.equals(orderType) && !sellOrders.isEmpty()) {
            return sellOrders.get(sellOrders.size() - 1);
        }
        return null;
    }
    
    /**
     * @return the last entry order recorded
     */
    public Order getLastEntry() {
        if (!entryOrders.isEmpty()) {
            return entryOrders.get(entryOrders.size() - 1);
        }
        return null;
    }
    
    /**
     * @return the last exit order recorded
     */
    public Order getLastExit() {
        if (!exitOrders.isEmpty()) {
            return exitOrders.get(exitOrders.size() - 1);
        }
        return null;
    }

    /**
     * Records an order and the corresponding trade (if closed).
     * @param order the order to be recorded
     * @param isEntry true if the order is an entry, false otherwise (exit)
     */
    private void recordOrder(Order order, boolean isEntry) {
        if (order == null) {
            throw new IllegalArgumentException("Order should not be null");
        }
        
        // Storing the new order in entries/exits lists
        if (isEntry) {
            entryOrders.add(order);
        } else {
            exitOrders.add(order);
        }
        
        // Storing the new order in orders list
        orders.add(order);
        if (OrderType.BUY.equals(order.getType())) {
            // Storing the new order in buy orders list
            buyOrders.add(order);
        } else if (OrderType.SELL.equals(order.getType())) {
            // Storing the new order in sell orders list
            sellOrders.add(order);
        }

        // Storing the trade if closed
        if (currentTrade.isClosed()) {
            trades.add(currentTrade);
            currentTrade = new Trade(startingType);
        }
    }
}
