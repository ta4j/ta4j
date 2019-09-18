/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2019 Ta4j Organization & respective
 * authors (see AUTHORS)
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
package org.ta4j.core;

import org.ta4j.core.cost.CostModel;
import org.ta4j.core.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

import java.io.Serializable;
import java.util.Objects;

/**
 * An order.
 *
 * The order is defined by:
 * <ul>
 * <li>the index (in the {@link BarSeries bar series}) it is executed
 * <li>a {@link OrderType type} (BUY or SELL)
 * <li>a pricePerAsset (optional)
 * <li>an amount to be (or that was) ordered (optional)
 * </ul>
 * A {@link Trade trade} is a pair of complementary orders.
 */
public class Order implements Serializable {

    private static final long serialVersionUID = -905474949010114150L;

    /**
     * The type of an {@link Order order}.
     *
     * A BUY corresponds to a <i>BID</i> order. A SELL corresponds to an <i>ASK</i>
     * order.
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

    /**
     * Type of the order
     */
    private OrderType type;

    /**
     * The index the order was executed
     */
    private int index;

    /**
     * The pricePerAsset for the order
     */
    private Num pricePerAsset;

    /**
     * The net price for the order, net transaction costs
     */
    private Num netPrice;

    /**
     * The amount to be (or that was) ordered
     */
    private Num amount;

    /**
     * Cost of executing the order
     */
    private Num cost;

    /** The cost model for order execution */
    private CostModel costModel;

    /**
     * Constructor.
     *
     * @param index  the index the order is executed
     * @param series the bar series
     * @param type   the type of the order
     */
    protected Order(int index, BarSeries series, OrderType type) {
        this(index, series, type, series.numOf(1));
    }

    /**
     * Constructor.
     *
     * @param index  the index the order is executed
     * @param series the bar series
     * @param type   the type of the order
     * @param amount the amount to be (or that was) ordered
     */
    protected Order(int index, BarSeries series, OrderType type, Num amount) {
        this(index, series, type, amount, new ZeroCostModel());
    }

    /**
     * Constructor.
     * 
     * @param index  the index the order is executed
     * @param series the bar series
     * @param type   the type of the order
     * @param amount the amount to be (or that was) ordered
     * @param transactionCostModel the cost model for order execution cost
     */
    protected Order(int index, BarSeries series, OrderType type, Num amount, CostModel transactionCostModel) {
        this.type = type;
        this.index = index;
        this.amount = amount;
        setPricesAndCost(series.getBar(index).getClosePrice(), amount, transactionCostModel);
    }

    /**
     * Constructor.
     *
     * @param index         the index the order is executed
     * @param type          the type of the order
     * @param pricePerAsset the pricePerAsset for the order
     */
    protected Order(int index, OrderType type, Num pricePerAsset) {
        this(index, type, pricePerAsset, pricePerAsset.numOf(1));
    }

    /**
     * Constructor.
     *
     * @param index         the index the order is executed
     * @param type          the type of the order
     * @param pricePerAsset the pricePerAsset for the order
     * @param amount        the amount to be (or that was) ordered
     */
    protected Order(int index, OrderType type, Num pricePerAsset, Num amount) {
        this(index, type, pricePerAsset, amount, new ZeroCostModel());
    }

    /**
     * Constructor.
     *
     * @param index                the index the order is executed
     * @param type                 the type of the order
     * @param pricePerAsset        the pricePerAsset for the order
     * @param amount               the amount to be (or that was) ordered
     * @param transactionCostModel the cost model for order execution
     */
    protected Order(int index, OrderType type, Num pricePerAsset, Num amount, CostModel transactionCostModel) {
        this.type = type;
        this.index = index;
        this.amount = amount;

        setPricesAndCost(pricePerAsset, amount, transactionCostModel);
    }

    /**
     * @return the type of the order (BUY or SELL)
     */
    public OrderType getType() {
        return type;
    }

    /**
     * @return the costs of the order
     */
    public Num getCost() {
        return cost;
    }

    /**
     * @return the index the order is executed
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return the pricePerAsset for the order
     */
    public Num getPricePerAsset() {
        return pricePerAsset;
    }

    /**
     * @return the pricePerAsset for the order, net transaction costs
     */
    public Num getNetPrice() {
        return netPrice;
    }

    /**
     * @return the amount to be (or that was) ordered
     */
    public Num getAmount() {
        return amount;
    }

    /**
     * @return the cost model for order execution
     */
    public CostModel getCostModel() {
        return costModel;
    }

    /**
     * Sets the raw and net prices of the order
     *
     * @param pricePerAsset        raw price of the asset
     * @param amount               amount of assets ordered
     * @param transactionCostModel the cost model for order execution
     */
    private void setPricesAndCost(Num pricePerAsset, Num amount, CostModel transactionCostModel) {
        this.costModel = transactionCostModel;
        this.pricePerAsset = pricePerAsset;
        this.cost = transactionCostModel.calculate(this.pricePerAsset, amount);

        Num costPerAsset = cost.dividedBy(amount);
        // add transaction costs to the pricePerAsset at the order
        if (type.equals(OrderType.BUY)) {
            this.netPrice = this.pricePerAsset.plus(costPerAsset);
        } else {
            this.netPrice = this.pricePerAsset.minus(costPerAsset);
        }
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

    @Override
    public int hashCode() {
        return Objects.hash(type, index, pricePerAsset, amount);
    }

    @Override
	  public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		final Order other = (Order) obj;
		return Objects.equals(type, other.type) 
			&& Objects.equals(index, other.index)
			&& Objects.equals(pricePerAsset, other.pricePerAsset) 
			&& Objects.equals(amount, other.amount);
	  }

    @Override
    public String toString() {
        return "Order{" + "type=" + type + ", index=" + index + ", price=" + pricePerAsset + ", amount=" + amount + '}';
    }

    /**
     * @param index  the index the order is executed
     * @param series the bar series
     * @return a BUY order
     */
    public static Order buyAt(int index, BarSeries series) {
        return new Order(index, series, OrderType.BUY);
    }

    /**
     * @param index  the index the order is executed
     * @param price  the price for the order
     * @param amount the amount to be (or that was) bought
     * @param transactionCostModel the cost model for order execution
     * @return a BUY order
     */
    public static Order buyAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new Order(index, OrderType.BUY, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the order is executed
     * @param price  the price for the order
     * @param amount the amount to be (or that was) bought
     * @return a BUY order
     */
    public static Order buyAt(int index, Num price, Num amount) {
        return new Order(index, OrderType.BUY, price, amount);
    }

    /**
     * @param index  the index the order is executed
     * @param series the bar series
     * @param amount the amount to be (or that was) bought
     * @return a BUY order
     */
    public static Order buyAt(int index, BarSeries series, Num amount) {
        return new Order(index, series, OrderType.BUY, amount);
    }

    /**
     * @param index  the index the order is executed
     * @param series the bar series
     * @param amount the amount to be (or that was) bought
     * @param transactionCostModel the cost model for order execution
     * @return a BUY order
     */
    public static Order buyAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new Order(index, series, OrderType.BUY, amount, transactionCostModel);
    }

    /**
     * @param index  the index the order is executed
     * @param series the bar series
     * @return a SELL order
     */
    public static Order sellAt(int index, BarSeries series) {
        return new Order(index, series, OrderType.SELL);
    }

    /**
     * @param index  the index the order is executed
     * @param price  the price for the order
     * @param amount the amount to be (or that was) sold
     * @return a SELL order
     */
    public static Order sellAt(int index, Num price, Num amount) {
        return new Order(index, OrderType.SELL, price, amount);
    }

    /**
     * @param index  the index the order is executed
     * @param price  the price for the order
     * @param amount the amount to be (or that was) sold
     * @param transactionCostModel the cost model for order execution
     * @return a SELL order
     */
    public static Order sellAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new Order(index, OrderType.SELL, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the order is executed
     * @param series the bar series
     * @param amount the amount to be (or that was) bought
     * @return a SELL order
     */
    public static Order sellAt(int index, BarSeries series, Num amount) {
        return new Order(index, series, OrderType.SELL, amount);
    }

    /**
     * @param index  the index the order is executed
     * @param series the bar series
     * @param amount the amount to be (or that was) bought
     * @param transactionCostModel the cost model for order execution
     * @return a SELL order
     */
    public static Order sellAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new Order(index, series, OrderType.SELL, amount, transactionCostModel);
    }

    /**
     * @return the value of an order (without transaction cost)
     */
    public Num getValue() {
        return pricePerAsset.multipliedBy(amount);
    }
}
