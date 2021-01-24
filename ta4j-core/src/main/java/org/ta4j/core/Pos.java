/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import java.io.Serializable;
import java.util.Objects;

import org.ta4j.core.cost.CostModel;
import org.ta4j.core.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * A position.
 *
 * The position is defined by:
 * <ul>
 * <li>the index (in the {@link BarSeries bar series}) it is created
 * <li>a {@link PosType type} (BUY or SELL)
 * <li>a pricePerAsset (optional)
 * <li>an amount (quantity of assets) of the position (optional)
 * </ul>
 * A {@link PosPair position pair} is a pair of complementary positions.
 */
public class Pos implements Serializable {

    private static final long serialVersionUID = -905474949010114150L;

    /**
     * The type of a {@link Pos position}.
     *
     * A BUY corresponds to a <i>BID</i> or <i>LONG</i> position. A SELL corresponds
     * to an <i>ASK</i> or <i>SHORT</i> position.
     */
    public enum PosType {

        BUY {
            @Override
            public PosType complementType() {
                return SELL;
            }
        },
        SELL {
            @Override
            public PosType complementType() {
                return BUY;
            }
        };

        /**
         * @return the complementary position type
         */
        public abstract PosType complementType();
    }

    /**
     * The type of the position
     */
    private PosType type;

    /**
     * The index the position was created
     */
    private int index;

    /**
     * The pricePerAsset of the position
     */
    private Num pricePerAsset;

    /**
     * The net price of the position, net transaction costs
     */
    private Num netPrice;

    /**
     * The position amount (quantity of assets)
     */
    private Num amount;

    /**
     * The cost of the order execution leading to the position
     */
    private Num cost;

    /** The cost model for the order execution leading to the position */
    private CostModel costModel;

    /**
     * Constructor.
     *
     * @param index  the index the position is created
     * @param series the bar series
     * @param type   the type of the position
     */
    protected Pos(int index, BarSeries series, PosType type) {
        this(index, series, type, series.numOf(1));
    }

    /**
     * Constructor.
     *
     * @param index  the index the position is created
     * @param series the bar series
     * @param type   the type of the position
     * @param amount the amount of the position
     */
    protected Pos(int index, BarSeries series, PosType type, Num amount) {
        this(index, series, type, amount, new ZeroCostModel());
    }

    /**
     * Constructor.
     * 
     * @param index                the index the position is created
     * @param series               the bar series
     * @param type                 the type of the position
     * @param amount               the amount of the position
     * @param transactionCostModel the cost model for order execution
     */
    protected Pos(int index, BarSeries series, PosType type, Num amount, CostModel transactionCostModel) {
        this.type = type;
        this.index = index;
        this.amount = amount;
        setPricesAndCost(series.getBar(index).getClosePrice(), amount, transactionCostModel);
    }

    /**
     * Constructor.
     *
     * @param index         the index the position is created
     * @param type          the type of the position
     * @param pricePerAsset the price per asset of the position
     */
    protected Pos(int index, PosType type, Num pricePerAsset) {
        this(index, type, pricePerAsset, pricePerAsset.numOf(1));
    }

    /**
     * Constructor.
     *
     * @param index         the index the position is created
     * @param type          the type of the position
     * @param pricePerAsset the price per asset of the position
     * @param amount        the amount of the position
     */
    protected Pos(int index, PosType type, Num pricePerAsset, Num amount) {
        this(index, type, pricePerAsset, amount, new ZeroCostModel());
    }

    /**
     * Constructor.
     *
     * @param index                the index the position is created
     * @param type                 the type of the position
     * @param pricePerAsset        the price per asset of the position
     * @param amount               the amount of the position
     * @param transactionCostModel the cost model for order execution
     */
    protected Pos(int index, PosType type, Num pricePerAsset, Num amount, CostModel transactionCostModel) {
        this.type = type;
        this.index = index;
        this.amount = amount;

        setPricesAndCost(pricePerAsset, amount, transactionCostModel);
    }

    /**
     * @return the type of the position (BUY or SELL)
     */
    public PosType getType() {
        return type;
    }

    /**
     * @return the costs for order execution leading to the position
     */
    public Num getCost() {
        return cost;
    }

    /**
     * @return the index the position is created
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return the pricePerAsset of the position
     */
    public Num getPricePerAsset() {
        return pricePerAsset;
    }

    /**
     * @return the pricePerAsset of the position, or, if <code>NaN</code>, the close
     *         price from the supplied {@link BarSeries}.
     */
    public Num getPricePerAsset(BarSeries barSeries) {
        if (pricePerAsset.isNaN()) {
            return barSeries.getBar(index).getClosePrice();
        }
        return pricePerAsset;
    }

    /**
     * @return the pricePerAsset of the position, net transaction costs
     */
    public Num getNetPrice() {
        return netPrice;
    }

    /**
     * @return the amount of the position (quantity of assets)
     */
    public Num getAmount() {
        return amount;
    }

    /**
     * @return the cost model for order execution leading to the position
     */
    public CostModel getCostModel() {
        return costModel;
    }

    /**
     * Sets the raw and net prices of the position
     *
     * @param pricePerAsset        the raw price of the asset
     * @param amount               the amount of the assets
     * @param transactionCostModel the cost model for order execution
     */
    private void setPricesAndCost(Num pricePerAsset, Num amount, CostModel transactionCostModel) {
        this.costModel = transactionCostModel;
        this.pricePerAsset = pricePerAsset;
        this.cost = transactionCostModel.calculate(this.pricePerAsset, amount);

        Num costPerAsset = cost.dividedBy(amount);
        // add transaction costs to the pricePerAsset to the position
        if (type.equals(PosType.BUY)) {
            this.netPrice = this.pricePerAsset.plus(costPerAsset);
        } else {
            this.netPrice = this.pricePerAsset.minus(costPerAsset);
        }
    }

    /**
     * @return true if this is a BUY position, false otherwise
     */
    public boolean isBuy() {
        return type == PosType.BUY;
    }

    /**
     * @return true if this is a SELL position, false otherwise
     */
    public boolean isSell() {
        return type == PosType.SELL;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, index, pricePerAsset, amount);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        final Pos other = (Pos) obj;
        return Objects.equals(type, other.type) && Objects.equals(index, other.index)
                && Objects.equals(pricePerAsset, other.pricePerAsset) && Objects.equals(amount, other.amount);
    }

    @Override
    public String toString() {
        return "Position{" + "type=" + type + ", index=" + index + ", price=" + pricePerAsset + ", amount=" + amount
                + '}';
    }

    /**
     * @param index  the index the position is created
     * @param series the bar series
     * @return a BUY position
     */
    public static Pos buyAt(int index, BarSeries series) {
        return new Pos(index, series, PosType.BUY);
    }

    /**
     * @param index                the index the position is created
     * @param price                the price of the position
     * @param amount               the amount of the position
     * @param transactionCostModel the cost model for order execution
     * @return a BUY position
     */
    public static Pos buyAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new Pos(index, PosType.BUY, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the position is created
     * @param price  the price of the position
     * @param amount the amount of the position
     * @return a BUY position
     */
    public static Pos buyAt(int index, Num price, Num amount) {
        return new Pos(index, PosType.BUY, price, amount);
    }

    /**
     * @param index  the index the position is created
     * @param series the bar series
     * @param amount the amount of the position
     * @return a BUY position
     */
    public static Pos buyAt(int index, BarSeries series, Num amount) {
        return new Pos(index, series, PosType.BUY, amount);
    }

    /**
     * @param index                the index the position is created
     * @param series               the bar series
     * @param amount               the amount of the position
     * @param transactionCostModel the cost model for order execution
     * @return a BUY position
     */
    public static Pos buyAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new Pos(index, series, PosType.BUY, amount, transactionCostModel);
    }

    /**
     * @param index  the index the position is created
     * @param series the bar series
     * @return a SELL position
     */
    public static Pos sellAt(int index, BarSeries series) {
        return new Pos(index, series, PosType.SELL);
    }

    /**
     * @param index  the index the position is created
     * @param price  the price of the position
     * @param amount the amount to be (or that was) sold
     * @return a SELL position
     */
    public static Pos sellAt(int index, Num price, Num amount) {
        return new Pos(index, PosType.SELL, price, amount);
    }

    /**
     * @param index                the index the position is created
     * @param price                the price of the position
     * @param amount               the amount to be (or that was) sold
     * @param transactionCostModel the cost model for order execution
     * @return a SELL position
     */
    public static Pos sellAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new Pos(index, PosType.SELL, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the position is created
     * @param series the bar series
     * @param amount the amount of the position
     * @return a SELL position
     */
    public static Pos sellAt(int index, BarSeries series, Num amount) {
        return new Pos(index, series, PosType.SELL, amount);
    }

    /**
     * @param index                the index the position is created
     * @param series               the bar series
     * @param amount               the amount of the position
     * @param transactionCostModel the cost model for order execution
     * @return a SELL position
     */
    public static Pos sellAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new Pos(index, series, PosType.SELL, amount, transactionCostModel);
    }

    /**
     * @return the overall position value (without transaction cost)
     */
    public Num getValue() {
        return pricePerAsset.multipliedBy(amount);
    }
}
