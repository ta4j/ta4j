/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import org.ta4j.core.num.Num;

import java.io.Serializable;
import java.util.Objects;

/**
 * An order.
 * <p></p>
 * The order is defined by:
 * <ul>
 *     <li>the index (in the {@link TimeSeries time series}) it is executed
 *     <li>a {@link OrderType type} (BUY or SELL)
 *     <li>a price (optional)
 *     <li>an amount to be (or that was) ordered (optional)
 * </ul>
 * A {@link Trade trade} is a pair of complementary orders.
 */
public class Order implements Serializable {

	private static final long serialVersionUID = -905474949010114150L;

	/**
     * The type of an {@link Order order}.
     * <p>
     * A BUY corresponds to a <i>BID</i> order.<p>
     * A SELL corresponds to an <i>ASK</i> order.
     */
    public enum OrderType {

        BUY {
            @Override
            public OrderType complementType() {
                return SELL;
            }
        },
        SELL {
            @Override
            public OrderType complementType() {
                return BUY;
            }
        };

        /**
         * @return the complementary order type
         */
        public abstract OrderType complementType();
    }
    
    /** Type of the order */
    private OrderType type;

    /** The index the order was executed */
    private int index;

    /** The price for the order */
    private Num price;
    
    /** The amount to be (or that was) ordered */
    private Num amount;
    
    /**
     * Constructor.
     * @param index the index the order is executed
     * @param series the time series
     * @param type the type of the order
     */
    protected Order(int index, TimeSeries series, OrderType type) {
        this.type = type;
        this.index = index;
        this.amount = series.numOf(1);
        this.price = series.getBar(index).getClosePrice();
    }

    /**
     * Constructor.
     * @param index the index the order is executed
     * @param type the type of the order
     * @param price the price for the order
     * @param amount the amount to be (or that was) ordered
     */
    protected Order(int index, OrderType type, Num price, Num amount) {
        this.type = type;
        this.index = index;
        this.price = price;
        this.amount = amount;
    }

    /**
     * @return the type of the order (BUY or SELL)
     */
    public OrderType getType() {
        return type;
    }

    /**
     * @return true if this is a BUY order, false otherwise
     */
    public boolean isBuy() {
        return type == OrderType.BUY;
    }

    /**
     * @return true if this is a SELL order, false otherwise
     */
    public boolean isSell() {
        return type == OrderType.SELL;
    }

    /**
     * @return the index the order is executed
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return the price for the order
     */
    public Num getPrice() {
        return price;
    }

    /**
     * @return the amount to be (or that was) ordered
     */
    public Num getAmount() {
        return amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, index, price, amount);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Order other = (Order) obj;
        if (this.type != other.type) {
            return false;
        }
        if (this.index != other.index) {
            return false;
        }
        return (this.price == other.price || (this.price != null && this.price.equals(other.price))) && (this.amount == other.amount || (this.amount != null && this.amount.equals(other.amount)));
    }

    @Override
    public String toString() {
        return "Order{" + "type=" + type + ", index=" + index + ", price=" + price + ", amount=" + amount + '}';
    }
    
    /**
     * @param index the index the order is executed
     * @return a BUY order
     */
    public static Order buyAt(int index, TimeSeries series) {
        return new Order(index, series, OrderType.BUY);
    }

    /**
     * @param index the index the order is executed
     * @param price the price for the order
     * @param amount the amount to be (or that was) bought
     * @return a BUY order
     */
    public static Order buyAt(int index, Num price, Num amount) {
        return new Order(index, OrderType.BUY, price, amount);
    }

    /**
     * @param index the index the order is executed
     * @return a SELL order
     */
    public static Order sellAt(int index, TimeSeries series) {
        return new Order(index, series, OrderType.SELL);
    }

    /**
     * @param index the index the order is executed
     * @param price the price for the order
     * @param amount the amount to be (or that was) sold
     * @return a SELL order
     */
    public static Order sellAt(int index, Num price, Num amount) {
        return new Order(index, OrderType.SELL, price, amount);
    }
}
