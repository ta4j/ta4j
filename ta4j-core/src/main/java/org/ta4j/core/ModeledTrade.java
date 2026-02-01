/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.util.Objects;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Modeled {@link Trade} implementation backed by a {@link CostModel}.
 *
 * <ul>
 * <li>the index (in the {@link BarSeries bar series}) on which the trade is
 * executed
 * <li>a {@link Trade.TradeType type} (BUY or SELL)
 * <li>a pricePerAsset (optional)
 * <li>a trade amount (optional)
 * </ul>
 *
 * A {@link Position position} is a pair of complementary trades.
 *
 * @since 0.22.2
 */
public class ModeledTrade implements Trade {

    @Serial
    private static final long serialVersionUID = -905474949010114150L;

    /** The type of the trade. */
    private final Trade.TradeType type;

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

    /**
     * The modeled execution cost for this trade, derived from the configured
     * {@link CostModel}.
     */
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
    protected ModeledTrade(int index, BarSeries series, Trade.TradeType type) {
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
    protected ModeledTrade(int index, BarSeries series, Trade.TradeType type, Num amount) {
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
    protected ModeledTrade(int index, BarSeries series, Trade.TradeType type, Num amount,
            CostModel transactionCostModel) {
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
    protected ModeledTrade(int index, Trade.TradeType type, Num pricePerAsset) {
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
    protected ModeledTrade(int index, Trade.TradeType type, Num pricePerAsset, Num amount) {
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
    protected ModeledTrade(int index, Trade.TradeType type, Num pricePerAsset, Num amount,
            CostModel transactionCostModel) {
        this.type = type;
        this.index = index;
        this.amount = amount;

        setPricesAndCost(pricePerAsset, amount, transactionCostModel);
    }

    @Override
    public Trade.TradeType getType() {
        return type;
    }

    @Override
    public Num getCost() {
        return cost;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public Num getPricePerAsset() {
        return pricePerAsset;
    }

    @Override
    public Num getPricePerAsset(BarSeries barSeries) {
        if (pricePerAsset.isNaN()) {
            return barSeries.getBar(index).getClosePrice();
        }
        return pricePerAsset;
    }

    @Override
    public Num getNetPrice() {
        return netPrice;
    }

    @Override
    public Num getAmount() {
        return amount;
    }

    @Override
    public CostModel getCostModel() {
        return costModel;
    }

    /**
     * Sets the raw and net prices of the trade.
     *
     * @param pricePerAsset        the raw price of the asset
     * @param amount               the amount of assets ordered
     * @param transactionCostModel the cost model for trade execution
     */
    private void setPricesAndCost(Num pricePerAsset, Num amount, CostModel transactionCostModel) {
        this.costModel = transactionCostModel;
        this.pricePerAsset = pricePerAsset;
        this.cost = transactionCostModel.calculate(this.pricePerAsset, amount);

        Num costPerAsset = cost.dividedBy(amount);
        // add transaction costs to the pricePerAsset at the trade
        if (type.equals(Trade.TradeType.BUY)) {
            this.netPrice = this.pricePerAsset.plus(costPerAsset);
        } else {
            this.netPrice = this.pricePerAsset.minus(costPerAsset);
        }
    }

    @Override
    public boolean isBuy() {
        return type == Trade.TradeType.BUY;
    }

    @Override
    public boolean isSell() {
        return type == Trade.TradeType.SELL;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, index, pricePerAsset, amount);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ModeledTrade other)) {
            return false;
        }
        return Objects.equals(type, other.type) && Objects.equals(index, other.index)
                && Objects.equals(pricePerAsset, other.pricePerAsset) && Objects.equals(amount, other.amount);
    }

    @Override
    public String toString() {
        return "Trade{" + "type=" + type + ", index=" + index + ", price=" + pricePerAsset + ", amount=" + amount + '}';
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @return a BUY trade
     * @since 0.22.2
     */
    public static ModeledTrade buyAt(int index, BarSeries series) {
        return new ModeledTrade(index, series, Trade.TradeType.BUY);
    }

    /**
     * @param index                the index the trade is executed
     * @param price                the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a BUY trade
     * @since 0.22.2
     */
    public static ModeledTrade buyAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new ModeledTrade(index, Trade.TradeType.BUY, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return a BUY trade
     * @since 0.22.2
     */
    public static ModeledTrade buyAt(int index, Num price, Num amount) {
        return new ModeledTrade(index, Trade.TradeType.BUY, price, amount);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param amount the trade amount
     * @return a BUY trade
     * @since 0.22.2
     */
    public static ModeledTrade buyAt(int index, BarSeries series, Num amount) {
        return new ModeledTrade(index, series, Trade.TradeType.BUY, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a BUY trade
     * @since 0.22.2
     */
    public static ModeledTrade buyAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new ModeledTrade(index, series, Trade.TradeType.BUY, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @return a SELL trade
     * @since 0.22.2
     */
    public static ModeledTrade sellAt(int index, BarSeries series) {
        return new ModeledTrade(index, series, Trade.TradeType.SELL);
    }

    /**
     * @param index  the index the trade is executed
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return a SELL trade
     * @since 0.22.2
     */
    public static ModeledTrade sellAt(int index, Num price, Num amount) {
        return new ModeledTrade(index, Trade.TradeType.SELL, price, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param price                the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a SELL trade
     * @since 0.22.2
     */
    public static ModeledTrade sellAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new ModeledTrade(index, Trade.TradeType.SELL, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param amount the trade amount
     * @return a SELL trade
     * @since 0.22.2
     */
    public static ModeledTrade sellAt(int index, BarSeries series, Num amount) {
        return new ModeledTrade(index, series, Trade.TradeType.SELL, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a SELL trade
     * @since 0.22.2
     */
    public static ModeledTrade sellAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new ModeledTrade(index, series, Trade.TradeType.SELL, amount, transactionCostModel);
    }

}
