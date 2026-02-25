/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.num.Num;

/**
 * Read-only trade contract shared by simulated trades and live executions.
 *
 * <ul>
 * <li>the index (in the {@link BarSeries bar series}) on which the trade is
 * executed</li>
 * <li>a {@link TradeType type} (BUY or SELL)</li>
 * <li>a price per asset (optional)</li>
 * <li>a trade amount (optional)</li>
 * </ul>
 *
 * <p>
 * Metadata fields (timestamp, instrument, ids) are optional and may return
 * {@code null}. They loosely mirror the attributes in XChange's trade DTO so
 * adapters can preserve exchange-provided identifiers when available.
 * </p>
 *
 * <p>
 * Use {@link SimulatedTrade} as the default simulated implementation and
 * {@link LiveTrade} for live fills.
 * </p>
 *
 * @since 0.22.2
 */
public interface Trade extends Serializable {

    /** The type of a trade. */
    enum TradeType {

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
     * @return the trade type (BUY or SELL)
     */
    TradeType getType();

    /**
     * @return the index the trade is executed
     */
    int getIndex();

    /**
     * @return the trade price per asset
     */
    Num getPricePerAsset();

    /**
     * @param barSeries the bar series
     * @return the trade price per asset, or, if {@code NaN}, the close price from
     *         the supplied {@link BarSeries}
     */
    default Num getPricePerAsset(BarSeries barSeries) {
        Num price = getPricePerAsset();
        if (price.isNaN()) {
            return barSeries.getBar(getIndex()).getClosePrice();
        }
        return price;
    }

    /**
     * @return the net price per asset for the trade (i.e.
     *         {@link #getPricePerAsset()} with trading costs)
     */
    Num getNetPrice();

    /**
     * @return the trade amount
     */
    Num getAmount();

    /**
     * @return the simulated costs of the trade as calculated by the configured
     *         {@link CostModel}
     */
    Num getCost();

    /**
     * @return the cost model for trade execution
     */
    CostModel getCostModel();

    /**
     * @return execution timestamp if available, otherwise {@code null}
     * @since 0.22.2
     */
    default Instant getTime() {
        return null;
    }

    /**
     * @return exchange-provided trade id if available, otherwise {@code null}
     * @since 0.22.2
     */
    default String getId() {
        return null;
    }

    /**
     * @return instrument identifier (symbol/pair) if available, otherwise
     *         {@code null}
     * @since 0.22.2
     */
    default String getInstrument() {
        return null;
    }

    /**
     * @return originating order id if available, otherwise {@code null}
     * @since 0.22.2
     */
    default String getOrderId() {
        return null;
    }

    /**
     * @return correlation id if available, otherwise {@code null}
     * @since 0.22.2
     */
    default String getCorrelationId() {
        return null;
    }

    /**
     * @return true if this is a BUY trade, false otherwise
     */
    default boolean isBuy() {
        return getType() == TradeType.BUY;
    }

    /**
     * @return true if this is a SELL trade, false otherwise
     */
    default boolean isSell() {
        return getType() == TradeType.SELL;
    }

    /**
     * @return the value of a trade (without transaction cost)
     */
    default Num getValue() {
        return getPricePerAsset().multipliedBy(getAmount());
    }

    /**
     * Returns execution fills for this trade.
     *
     * <p>
     * Default simulated trades expose a single fill. Aggregated/partial trades may
     * return multiple fills.
     * </p>
     *
     * @return execution fills of this trade
     * @since 0.22.2
     */
    default List<TradeFill> getFills() {
        return List.of(new TradeFill(getIndex(), getPricePerAsset(), getAmount()));
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @return a BUY trade
     */
    static Trade buyAt(int index, BarSeries series) {
        return new SimulatedTrade(index, series, TradeType.BUY);
    }

    /**
     * @param index                the index the trade is executed
     * @param price                the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a BUY trade
     */
    static Trade buyAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new SimulatedTrade(index, TradeType.BUY, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return a BUY trade
     */
    static Trade buyAt(int index, Num price, Num amount) {
        return new SimulatedTrade(index, TradeType.BUY, price, amount);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param amount the trade amount
     * @return a BUY trade
     */
    static Trade buyAt(int index, BarSeries series, Num amount) {
        return new SimulatedTrade(index, series, TradeType.BUY, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a BUY trade
     */
    static Trade buyAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new SimulatedTrade(index, series, TradeType.BUY, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @return a SELL trade
     */
    static Trade sellAt(int index, BarSeries series) {
        return new SimulatedTrade(index, series, TradeType.SELL);
    }

    /**
     * @param index  the index the trade is executed
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return a SELL trade
     */
    static Trade sellAt(int index, Num price, Num amount) {
        return new SimulatedTrade(index, TradeType.SELL, price, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param price                the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a SELL trade
     */
    static Trade sellAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new SimulatedTrade(index, TradeType.SELL, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param amount the trade amount
     * @return a SELL trade
     */
    static Trade sellAt(int index, BarSeries series, Num amount) {
        return new SimulatedTrade(index, series, TradeType.SELL, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a SELL trade
     */
    static Trade sellAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new SimulatedTrade(index, series, TradeType.SELL, amount, transactionCostModel);
    }
}
