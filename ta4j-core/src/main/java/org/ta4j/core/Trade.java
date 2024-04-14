/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.num.Num;

/**
 * A {@code Trade} is defined by:
 *
 * <ul>
 * <li>the index (in the {@link BarSeries bar series}) on which the trade is
 * executed
 * <li>a {@link TradeType type} (BUY or SELL)
 * <li>a pricePerAsset (optional)
 * <li>a trade amount (optional)
 * </ul>
 *
 * A {@link Position position} is a pair of complementary trades.
 */
public class Trade implements Serializable {

    private static final long serialVersionUID = -905474949010114150L;

    /** The type of a {@link Trade trade}. */
    public enum TradeType {

        /** A BUY corresponds to a <i>BID</i> trade. */
        BUY {
            @Override
            public TradeType complementType() {
                return SELL;
            }
        },

        /** A SELL corresponds to an <i>ASK</i> trade. */
        SELL {
            @Override
            public TradeType complementType() {
                return BUY;
            }
        };

        /**
         * @return the complementary trade type
         */
        public abstract TradeType complementType();
    }

    /** The type of the trade. */
    private final TradeType type;

    /** The index the trade was executed. */
    private final int index;

    /** The trade price per asset. */
    private Num pricePerAsset;

    /**
     * The net price per asset for the trade (i.e. {@link #pricePerAsset} with
     * {@link #cost}).
     */
    private Num netPrice;

    /** The trade amount. */
    private final Num amount;

    /** The cost for executing the trade. */
    private Num cost;

    /** The cost model for trade execution. */
    private transient CostModel costModel;

    /**
     * Constructor.
     *
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param type   the trade type
     */
    protected Trade(final int index, final BacktestBarSeries series, final TradeType type) {
        this(index, series, type, series.numFactory().one());
    }

    /**
     * Constructor.
     *
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param type   the trade type
     * @param amount the trade amount
     */
    protected Trade(final int index, final BacktestBarSeries series, final TradeType type, final Num amount) {
        this(index, series, type, amount, new ZeroCostModel());
    }

    /**
     * Constructor.
     *
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param type                 the trade type
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution cost
     */
    protected Trade(final int index, final BacktestBarSeries series, final TradeType type, final Num amount,
            final CostModel transactionCostModel) {
        this.type = type;
        this.index = index;
        this.amount = amount;
        setPricesAndCost(series.getBar(index).getClosePrice(), amount, transactionCostModel);
    }

    /**
     * Constructor.
     *
     * @param index         the index the trade is executed
     * @param type          the trade type
     * @param pricePerAsset the trade price per asset
     */
    protected Trade(final int index, final TradeType type, final Num pricePerAsset) {
        this(index, type, pricePerAsset, pricePerAsset.getNumFactory().one());
    }

    /**
     * Constructor.
     *
     * @param index         the index the trade is executed
     * @param type          the trade type
     * @param pricePerAsset the trade price per asset
     * @param amount        the trade amount
     */
    protected Trade(final int index, final TradeType type, final Num pricePerAsset, final Num amount) {
        this(index, type, pricePerAsset, amount, new ZeroCostModel());
    }

    /**
     * Constructor.
     *
     * @param index                the index the trade is executed
     * @param type                 the trade type
     * @param pricePerAsset        the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     */
    protected Trade(final int index, final TradeType type, final Num pricePerAsset, final Num amount,
            final CostModel transactionCostModel) {
        this.type = type;
        this.index = index;
        this.amount = amount;

        setPricesAndCost(pricePerAsset, amount, transactionCostModel);
    }

    /**
     * @return the trade type (BUY or SELL)
     */
    public TradeType getType() {
        return this.type;
    }

    /**
     * @return the costs of the trade
     */
    public Num getCost() {
        return this.cost;
    }

    /**
     * @return the index the trade is executed
     */
    public int getIndex() {
        return this.index;
    }

    /**
     * @return the trade price per asset
     */
    public Num getPricePerAsset() {
        return this.pricePerAsset;
    }

    /**
     * @return the trade price per asset, or, if {@code NaN}, the close price from
     *         the supplied {@link BarSeries}.
     */
    public Num getPricePerAsset(final BacktestBarSeries barSeries) {
        if (this.pricePerAsset.isNaN()) {
            return barSeries.getBar(this.index).getClosePrice();
        }
        return this.pricePerAsset;
    }

    /**
     * @return the net price per asset for the trade (i.e. {@link #pricePerAsset}
     *         with {@link #cost})
     */
    public Num getNetPrice() {
        return this.netPrice;
    }

    /**
     * @return the trade amount
     */
    public Num getAmount() {
        return this.amount;
    }

    /**
     * @return the cost model for trade execution
     */
    public CostModel getCostModel() {
        return this.costModel;
    }

    /**
     * Sets the raw and net prices of the trade.
     *
     * @param pricePerAsset        the raw price of the asset
     * @param amount               the amount of assets ordered
     * @param transactionCostModel the cost model for trade execution
     */
    private void setPricesAndCost(final Num pricePerAsset, final Num amount, final CostModel transactionCostModel) {
        this.costModel = transactionCostModel;
        this.pricePerAsset = pricePerAsset;
        this.cost = transactionCostModel.calculate(this.pricePerAsset, amount);

        final Num costPerAsset = this.cost.dividedBy(amount);
        // add transaction costs to the pricePerAsset at the trade
        if (this.type.equals(TradeType.BUY)) {
            this.netPrice = this.pricePerAsset.plus(costPerAsset);
        } else {
            this.netPrice = this.pricePerAsset.minus(costPerAsset);
        }
    }

    /**
     * @return true if this is a BUY trade, false otherwise
     */
    public boolean isBuy() {
        return this.type == TradeType.BUY;
    }

    /**
     * @return true if this is a SELL trade, false otherwise
     */
    public boolean isSell() {
        return this.type == TradeType.SELL;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.type, this.index, this.pricePerAsset, this.amount);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        final Trade other = (Trade) obj;
        return Objects.equals(this.type, other.type) && Objects.equals(this.index, other.index)
                && Objects.equals(this.pricePerAsset, other.pricePerAsset) && Objects.equals(this.amount, other.amount);
    }

    @Override
    public String toString() {
        return "Trade{" + "type=" + this.type + ", index=" + this.index + ", price=" + this.pricePerAsset + ", amount="
                + this.amount + '}';
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @return a BUY trade
     */
    public static Trade buyAt(final int index, final BacktestBarSeries series) {
        return new Trade(index, series, TradeType.BUY);
    }

    /**
     * @param index                the index the trade is executed
     * @param price                the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a BUY trade
     */
    public static Trade buyAt(final int index, final Num price, final Num amount,
            final CostModel transactionCostModel) {
        return new Trade(index, TradeType.BUY, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return a BUY trade
     */
    public static Trade buyAt(final int index, final Num price, final Num amount) {
        return new Trade(index, TradeType.BUY, price, amount);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param amount the trade amount
     * @return a BUY trade
     */
    public static Trade buyAt(final int index, final BacktestBarSeries series, final Num amount) {
        return new Trade(index, series, TradeType.BUY, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a BUY trade
     */
    public static Trade buyAt(final int index, final BacktestBarSeries series, final Num amount,
            final CostModel transactionCostModel) {
        return new Trade(index, series, TradeType.BUY, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @return a SELL trade
     */
    public static Trade sellAt(final int index, final BacktestBarSeries series) {
        return new Trade(index, series, TradeType.SELL);
    }

    /**
     * @param index  the index the trade is executed
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return a SELL trade
     */
    public static Trade sellAt(final int index, final Num price, final Num amount) {
        return new Trade(index, TradeType.SELL, price, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param price                the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a SELL trade
     */
    public static Trade sellAt(final int index, final Num price, final Num amount,
            final CostModel transactionCostModel) {
        return new Trade(index, TradeType.SELL, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param amount the trade amount
     * @return a SELL trade
     */
    public static Trade sellAt(final int index, final BacktestBarSeries series, final Num amount) {
        return new Trade(index, series, TradeType.SELL, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a SELL trade
     */
    public static Trade sellAt(final int index, final BacktestBarSeries series, final Num amount,
            final CostModel transactionCostModel) {
        return new Trade(index, series, TradeType.SELL, amount, transactionCostModel);
    }

    /**
     * @return the value of a trade (without transaction cost)
     */
    public Num getValue() {
        return this.pricePerAsset.multipliedBy(this.amount);
    }
}
