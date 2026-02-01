/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.num.Num;

/**
 * Backwards-compatible shim for modeled trades.
 *
 * <p>
 * Use {@link ModeledTrade} for modeled/backtest trades or {@link LiveTrade} for
 * live executions, both of which implement {@link TradeView}.
 * </p>
 *
 * @since 0.22.2
 * @deprecated Use {@link ModeledTrade} or {@link LiveTrade} with
 *             {@link TradeView}.
 */
@Deprecated(since = "0.22.2")
public class Trade extends ModeledTrade {

    @Serial
    private static final long serialVersionUID = 3187955097391865935L;

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

    /**
     * Constructor.
     *
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param type   the trade type
     */
    protected Trade(int index, BarSeries series, TradeType type) {
        super(index, series, type);
    }

    /**
     * Constructor.
     *
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param type   the trade type
     * @param amount the trade amount
     */
    protected Trade(int index, BarSeries series, TradeType type, Num amount) {
        super(index, series, type, amount);
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
    protected Trade(int index, BarSeries series, TradeType type, Num amount, CostModel transactionCostModel) {
        super(index, series, type, amount, transactionCostModel);
    }

    /**
     * Constructor.
     *
     * @param index         the index the trade is executed
     * @param type          the trade type
     * @param pricePerAsset the trade price per asset
     */
    protected Trade(int index, TradeType type, Num pricePerAsset) {
        super(index, type, pricePerAsset);
    }

    /**
     * Constructor.
     *
     * @param index         the index the trade is executed
     * @param type          the trade type
     * @param pricePerAsset the trade price per asset
     * @param amount        the trade amount
     */
    protected Trade(int index, TradeType type, Num pricePerAsset, Num amount) {
        super(index, type, pricePerAsset, amount);
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
    protected Trade(int index, TradeType type, Num pricePerAsset, Num amount, CostModel transactionCostModel) {
        super(index, type, pricePerAsset, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @return a BUY trade
     */
    public static Trade buyAt(int index, BarSeries series) {
        return new Trade(index, series, TradeType.BUY);
    }

    /**
     * @param index                the index the trade is executed
     * @param price                the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a BUY trade
     */
    public static Trade buyAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new Trade(index, TradeType.BUY, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return a BUY trade
     */
    public static Trade buyAt(int index, Num price, Num amount) {
        return new Trade(index, TradeType.BUY, price, amount);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param amount the trade amount
     * @return a BUY trade
     */
    public static Trade buyAt(int index, BarSeries series, Num amount) {
        return new Trade(index, series, TradeType.BUY, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a BUY trade
     */
    public static Trade buyAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new Trade(index, series, TradeType.BUY, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @return a SELL trade
     */
    public static Trade sellAt(int index, BarSeries series) {
        return new Trade(index, series, TradeType.SELL);
    }

    /**
     * @param index  the index the trade is executed
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return a SELL trade
     */
    public static Trade sellAt(int index, Num price, Num amount) {
        return new Trade(index, TradeType.SELL, price, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param price                the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a SELL trade
     */
    public static Trade sellAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new Trade(index, TradeType.SELL, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param amount the trade amount
     * @return a SELL trade
     */
    public static Trade sellAt(int index, BarSeries series, Num amount) {
        return new Trade(index, series, TradeType.SELL, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a SELL trade
     */
    public static Trade sellAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new Trade(index, series, TradeType.SELL, amount, transactionCostModel);
    }
}
