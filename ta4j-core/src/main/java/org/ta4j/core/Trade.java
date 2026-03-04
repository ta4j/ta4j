/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
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
 * Use {@link BaseTrade} as the default implementation for both simulation and
 * live-recorded fills.
 * </p>
 *
 * <p>
 * {@link SimulatedTrade} and {@link LiveTrade} are retained as deprecated
 * compatibility wrappers over {@link BaseTrade}.
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
     * return multiple fills. The default single fill mirrors trade-level metadata
     * (time, fee, order/correlation ids) when available.
     * </p>
     *
     * @return execution fills of this trade
     * @since 0.22.4
     */
    default List<TradeFill> getFills() {
        ExecutionSide side = getType() == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
        return List.of(new TradeFill(getIndex(), getTime(), getPricePerAsset(), getAmount(), getCost(), side,
                getOrderId(), getCorrelationId()));
    }

    /**
     * Resolves execution fills for the provided trade.
     *
     * <p>
     * Trades should expose fills via {@link #getFills()}. When an implementation
     * returns an empty list, this method falls back to index/price/amount to
     * preserve compatibility with legacy scalar trade semantics.
     * </p>
     *
     * @param trade trade to inspect
     * @return immutable execution fills for the trade
     * @since 0.22.4
     */
    static List<TradeFill> executionFillsOf(Trade trade) {
        Objects.requireNonNull(trade, "trade");
        List<TradeFill> fills = List.copyOf(trade.getFills());
        if (!fills.isEmpty()) {
            return fills;
        }
        ExecutionSide side = trade.getType() == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
        return List.of(new TradeFill(trade.getIndex(), trade.getTime(), trade.getPricePerAsset(), trade.getAmount(),
                trade.getCost(), side, trade.getOrderId(), trade.getCorrelationId()));
    }

    /**
     * Creates a trade from one or more execution fills using zero transaction
     * costs.
     *
     * @param type  trade type
     * @param fills execution fills (must not be empty)
     * @return a trade representing the provided fills
     * @since 0.22.4
     */
    static Trade fromFills(TradeType type, List<TradeFill> fills) {
        return fromFills(type, fills, new ZeroCostModel());
    }

    /**
     * Creates a trade from one or more execution fills.
     *
     * <p>
     * The returned trade is a {@link BaseTrade}. Single-fill inputs keep scalar
     * semantics; multi-fill inputs preserve full fill progression while exposing
     * aggregated price/amount views.
     * </p>
     *
     * @param type                 trade type
     * @param fills                execution fills (must not be empty)
     * @param transactionCostModel transaction cost model
     * @return a trade representing the provided fills
     * @throws IllegalArgumentException when fills are empty or invalid
     * @since 0.22.4
     */
    static Trade fromFills(TradeType type, List<TradeFill> fills, CostModel transactionCostModel) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(fills, "fills");
        Objects.requireNonNull(transactionCostModel, "transactionCostModel");
        if (fills.isEmpty()) {
            throw new IllegalArgumentException("fills must not be empty");
        }
        return new BaseTrade(type, fills, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @return a BUY trade
     */
    static Trade buyAt(int index, BarSeries series) {
        return new BaseTrade(index, series, TradeType.BUY);
    }

    /**
     * @param index                the index the trade is executed
     * @param price                the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a BUY trade
     */
    static Trade buyAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new BaseTrade(index, TradeType.BUY, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return a BUY trade
     */
    static Trade buyAt(int index, Num price, Num amount) {
        return new BaseTrade(index, TradeType.BUY, price, amount);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param amount the trade amount
     * @return a BUY trade
     */
    static Trade buyAt(int index, BarSeries series, Num amount) {
        return new BaseTrade(index, series, TradeType.BUY, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a BUY trade
     */
    static Trade buyAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new BaseTrade(index, series, TradeType.BUY, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @return a SELL trade
     */
    static Trade sellAt(int index, BarSeries series) {
        return new BaseTrade(index, series, TradeType.SELL);
    }

    /**
     * @param index  the index the trade is executed
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return a SELL trade
     */
    static Trade sellAt(int index, Num price, Num amount) {
        return new BaseTrade(index, TradeType.SELL, price, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param price                the trade price per asset
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a SELL trade
     */
    static Trade sellAt(int index, Num price, Num amount, CostModel transactionCostModel) {
        return new BaseTrade(index, TradeType.SELL, price, amount, transactionCostModel);
    }

    /**
     * @param index  the index the trade is executed
     * @param series the bar series
     * @param amount the trade amount
     * @return a SELL trade
     */
    static Trade sellAt(int index, BarSeries series, Num amount) {
        return new BaseTrade(index, series, TradeType.SELL, amount);
    }

    /**
     * @param index                the index the trade is executed
     * @param series               the bar series
     * @param amount               the trade amount
     * @param transactionCostModel the cost model for trade execution
     * @return a SELL trade
     */
    static Trade sellAt(int index, BarSeries series, Num amount, CostModel transactionCostModel) {
        return new BaseTrade(index, series, TradeType.SELL, amount, transactionCostModel);
    }
}
