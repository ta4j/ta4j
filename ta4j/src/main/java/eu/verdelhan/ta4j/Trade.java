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

/**
 * Set of two {@link Order orders}. Not a single order.
 * 
 */
public class Trade {

    private Order entry;

    private Order exit;

    private OrderType startingType;

    /**
     * Constructor.
     */
    public Trade() {
        this(OrderType.BUY);
    }

    /**
     * Constructor.
     * @param startingType the starting {@link OrderType order type} of the trade
     */
    public Trade(OrderType startingType) {
        if (startingType == null) {
            throw new IllegalArgumentException("Starting type must not be null");
        }
        this.startingType = startingType;
    }

    /**
     * Constructor.
     * @param entry the entry {@link Order order}
     * @param exit the exit {@link Order order}
     */
    public Trade(Order entry, Order exit) {
        if (entry.getType().equals(exit.getType())) {
            throw new IllegalArgumentException("Both orders must have different types");
        }
        this.entry = entry;
        this.exit = exit;
    }

    /**
     * @return the entry {@link Order order} of the trade
     */
    public Order getEntry() {
        return entry;
    }

    /**
     * @return the exit {@link Order order} of the trade
     */
    public Order getExit() {
        return exit;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Trade) {
            Trade t = (Trade) obj;
            return entry.equals(t.getEntry()) && exit.equals(t.getExit());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (entry.hashCode() * 31) + (exit.hashCode() * 17);
    }

    /**
     * Operates the trade at the i-th position
     * @param i the index
     */
    public void operate(int i) {
        if (isNew()) {
            entry = new Order(i, startingType);
        } else if (isOpened()) {
            if (i < entry.getIndex()) {
                throw new IllegalStateException("The index i is less than the entryOrder index");
            }
            exit = new Order(i, startingType.complementType());
        }
    }

    /**
     * @return true if the trade is closed, false otherwise
     */
    public boolean isClosed() {
        return (entry != null) && (exit != null);
    }

    /**
     * @return true if the trade is opened, false otherwise
     */
    public boolean isOpened() {
        return (entry != null) && (exit == null);
    }

    /**
     * @return true if the trade is new, false otherwise
     */
    public boolean isNew() {
        return (entry == null) && (exit == null);
    }

    @Override
    public String toString() {
        return "Entry: " + entry + " exit: " + exit;
    }
}
