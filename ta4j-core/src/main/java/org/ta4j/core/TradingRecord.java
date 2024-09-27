/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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

import static org.ta4j.core.num.NaN.NaN;

import java.io.Serializable;
import java.util.List;

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
            return positions.get(positions.size() - 1);
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
    Trade getLastTrade();

    /**
     * @param tradeType the type of the trade to get the last of
     * @return the last trade (of the provided type) recorded
     */
    Trade getLastTrade(TradeType tradeType);

    /**
     * @return the last entry trade recorded
     */
    Trade getLastEntry();

    /**
     * @return the last exit trade recorded
     */
    Trade getLastExit();

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
