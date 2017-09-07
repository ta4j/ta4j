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

import java.util.ArrayList;
import java.util.List;

/**
 * Base implementation of a {@link TradingRecord}.
 * <p>
 */
public class BaseTradingRecord implements TradingRecord {

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
    public BaseTradingRecord() {
        this(OrderType.BUY);
    }
    
    /**
     * Constructor.
     * @param entryOrderType the {@link OrderType order type} of entries in the trading session
     */
    public BaseTradingRecord(OrderType entryOrderType) {
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
    public BaseTradingRecord(Order... orders) {
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
    
    @Override
    public Trade getCurrentTrade() {
        return currentTrade;
    }
    
    @Override
    public void operate(int index, Decimal price, Decimal amount) {
        if (currentTrade.isClosed()) {
            // Current trade closed, should not occur
            throw new IllegalStateException("Current trade should not be closed");
        }
        boolean newOrderWillBeAnEntry = currentTrade.isNew();
        Order newOrder = currentTrade.operate(index, price, amount);
        recordOrder(newOrder, newOrderWillBeAnEntry);
    }
    
    @Override
    public boolean enter(int index, Decimal price, Decimal amount) {
        if (currentTrade.isNew()) {
            operate(index, price, amount);
            return true;
        }
        return false;
    }
    
    @Override
    public final boolean exit(int index, Decimal price, Decimal amount) {
        if (currentTrade.isOpened()) {
            operate(index, price, amount);
            return true;
        }
        return false;
    }
    
    @Override
    public List<Trade> getTrades() {
        return trades;
    }
    
    @Override
    public Order getLastOrder() {
        if (!orders.isEmpty()) {
            return orders.get(orders.size() - 1);
        }
        return null;
    }
    
    @Override
    public Order getLastOrder(OrderType orderType) {
        if (OrderType.BUY.equals(orderType) && !buyOrders.isEmpty()) {
            return buyOrders.get(buyOrders.size() - 1);
        } else if (OrderType.SELL.equals(orderType) && !sellOrders.isEmpty()) {
            return sellOrders.get(sellOrders.size() - 1);
        }
        return null;
    }
    
    @Override
    public Order getLastEntry() {
        if (!entryOrders.isEmpty()) {
            return entryOrders.get(entryOrders.size() - 1);
        }
        return null;
    }
    
    @Override
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
