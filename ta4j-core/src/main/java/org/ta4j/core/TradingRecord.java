/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.ta4j.core.num.NaN.NaN;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.num.Num;

/**
 * A {@code TradingRecord} holds the full history/record of a trading session
 * when running a {@link Strategy strategy}. It can be used to:
 *
 * <ul>
 * <li>analyze the performance of a {@link Strategy strategy}
 * <li>check whether some {@link Rule rules} are satisfied (while running a
 * strategy)
 * </ul>
 *
 * <p>
 * {@link Trade} is the public trade contract. Concrete trade implementations
 * are internal details and should not be required by strategy or backtest code.
 * </p>
 *
 * <p>
 * Execution metadata on trades/fills ({@code time}, {@code side},
 * {@code orderId}, {@code correlationId}) may be missing in simulated
 * environments. Implementations should preserve this metadata when provided and
 * apply deterministic fallbacks when it is absent.
 * </p>
 */
public interface TradingRecord extends Serializable {

    /**
     * @return the entry type (BUY or SELL) of the first trade in the trading
     *         session
     */
    TradeType getStartingType();

    /**
     * @return the name of the TradingRecord
     */
    String getName();

    /**
     * Places a trade in the trading record.
     *
     * @param index the index to place the trade
     */
    default void operate(int index) {
        operate(index, NaN, NaN);
    }

    /**
     * Places a trade in the trading record.
     *
     * @param index  the index to place the trade
     * @param price  the trade price per asset
     * @param amount the trade amount
     */
    void operate(int index, Num price, Num amount);

    /**
     * Places a pre-built trade in the trading record.
     *
     * <p>
     * This is useful for execution models that aggregate partial fills into a
     * single entry or exit trade.
     * </p>
     *
     * <p>
     * The default implementation delegates to {@link #operate(int, Num, Num)} and
     * therefore supports only index/price/amount semantics. Implementations that
     * store additional execution metadata should override this method.
     * </p>
     *
     * @param trade the trade to place
     * @throws UnsupportedOperationException if {@code trade} contains multiple
     *                                       fills and this implementation has not
     *                                       overridden this method
     * @since 0.22.4
     */
    default void operate(Trade trade) {
        Objects.requireNonNull(trade, "trade");
        List<TradeFill> fills = Trade.executionFillsOf(trade);
        if (fills.size() > 1) {
            throw new UnsupportedOperationException(
                    "This TradingRecord implementation must override operate(Trade) to preserve multi-fill trades");
        }
        TradeFill fill = fills.getFirst();
        operate(fill.index(), fill.price(), fill.amount());
    }

    /**
     * Places an entry trade in the trading record.
     *
     * @param index the index to place the entry
     * @return true if the entry has been placed, false otherwise
     */
    default boolean enter(int index) {
        return enter(index, NaN, NaN);
    }

    /**
     * Places an entry trade in the trading record.
     *
     * @param index  the index to place the entry
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return true if the entry has been placed, false otherwise
     */
    boolean enter(int index, Num price, Num amount);

    /**
     * Places an entry trade in the trading record.
     *
     * @param trade the entry trade to place
     * @return true if the entry has been placed, false otherwise
     * @throws IllegalArgumentException when trade type is not the configured entry
     *                                  type
     * @since 0.22.4
     */
    default boolean enter(Trade trade) {
        Objects.requireNonNull(trade, "trade");
        TradeType expectedEntryType = getStartingType();
        if (trade.getType() != expectedEntryType) {
            throw new IllegalArgumentException("Entry trade type must be " + expectedEntryType);
        }
        if (isClosed()) {
            operate(trade);
            return true;
        }
        return false;
    }

    /**
     * Places an exit trade in the trading record.
     *
     * @param index the index to place the exit
     * @return true if the exit has been placed, false otherwise
     */
    default boolean exit(int index) {
        return exit(index, NaN, NaN);
    }

    /**
     * Places an exit trade in the trading record.
     *
     * @param index  the index to place the exit
     * @param price  the trade price per asset
     * @param amount the trade amount
     * @return true if the exit has been placed, false otherwise
     */
    boolean exit(int index, Num price, Num amount);

    /**
     * Places an exit trade in the trading record.
     *
     * @param trade the exit trade to place
     * @return true if the exit has been placed, false otherwise
     * @throws IllegalArgumentException when trade type is not the configured exit
     *                                  type
     * @since 0.22.4
     */
    default boolean exit(Trade trade) {
        Objects.requireNonNull(trade, "trade");
        TradeType expectedExitType = getStartingType().complementType();
        if (trade.getType() != expectedExitType) {
            throw new IllegalArgumentException("Exit trade type must be " + expectedExitType);
        }
        if (!isClosed()) {
            operate(trade);
            return true;
        }
        return false;
    }

    /**
     * @return true if no position is open, false otherwise
     */
    default boolean isClosed() {
        return !getCurrentPosition().isOpened();
    }

    /**
     * @return the transaction cost model
     */
    CostModel getTransactionCostModel();

    /**
     * @return holding cost model
     */
    CostModel getHoldingCostModel();

    /**
     * @return the recorded closed positions
     */
    List<Position> getPositions();

    /**
     * @return the number of recorded closed positions
     */
    default int getPositionCount() {
        return getPositions().size();
    }

    /**
     * @return the current (open) position
     */
    Position getCurrentPosition();

    /**
     * @return the last closed position recorded
     */
    default Position getLastPosition() {
        List<Position> positions = getPositions();
        if (!positions.isEmpty()) {
            return positions.getLast();
        }
        return null;
    }

    /**
     * @return the trades recorded
     */
    List<Trade> getTrades();

    /**
     * @return the last trade recorded
     */
    default Trade getLastTrade() {
        List<Trade> trades = getTrades();
        if (!trades.isEmpty()) {
            return trades.getLast();
        }
        return null;
    }

    /**
     * @param tradeType the type of the trade to get the last of
     * @return the last trade (of the provided type) recorded
     */
    default Trade getLastTrade(TradeType tradeType) {
        List<Trade> trades = getTrades();
        for (int i = trades.size() - 1; i >= 0; i--) {
            Trade trade = trades.get(i);
            if (trade.getType() == tradeType) {
                return trade;
            }
        }
        return null;
    }

    /**
     * @return the last entry trade recorded
     */
    default Trade getLastEntry() {
        return getLastTrade(getStartingType());
    }

    /**
     * @return the last exit trade recorded
     */
    default Trade getLastExit() {
        return getLastTrade(getStartingType().complementType());
    }

    /**
     * @return the start of the recording (included)
     */
    Integer getStartIndex();

    /**
     * @return the end of the recording (included)
     */
    Integer getEndIndex();

    /**
     * @param series the bar series, not null
     * @return the {@link #getStartIndex()} if not null and greater than
     *         {@link BarSeries#getBeginIndex()}, otherwise
     *         {@link BarSeries#getBeginIndex()}
     */
    default int getStartIndex(BarSeries series) {
        return getStartIndex() == null ? series.getBeginIndex() : Math.max(getStartIndex(), series.getBeginIndex());
    }

    /**
     * @param series the bar series, not null
     * @return the {@link #getEndIndex()} if not null and less than
     *         {@link BarSeries#getEndIndex()}, otherwise
     *         {@link BarSeries#getEndIndex()}
     */
    default int getEndIndex(BarSeries series) {
        return getEndIndex() == null ? series.getEndIndex() : Math.min(getEndIndex(), series.getEndIndex());
    }
}
