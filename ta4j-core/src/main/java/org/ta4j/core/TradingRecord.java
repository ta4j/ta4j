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

import static org.ta4j.core.num.NaN.NaN;

import java.io.Serializable;
import java.util.List;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.num.Num;

/**
 * A history/record of a trading session.
 * * 交易时段的历史/记录。
 *
 * Holds the full trading record when running a {@link Strategy strategy}. It is used to:
 * * 在运行 {@link Strategy strategy} 时保持完整的交易记录。 它用于：
 *
 * <ul>
 * <li>check to satisfaction of some trading rules (when running a strategy) analyze the performance of a trading strategy
 * * <li>检查是否满足某些交易规则（在运行策略时）分析交易策略的表现
 * </ul>
 */
public interface TradingRecord extends Serializable {

    /**
     * @return the entry type (BUY or SELL) of the first trade in the trading    session
     * * @return 交易时段内第一笔交易的入场类型（买入或卖出）
     */
    TradeType getStartingType();

    /**
     * @return the name of the TradingRecord
     * @return 交易记录的名称
     */
    String getName();

    /**
     * Places a trade in the trading record.
     * * 在交易记录中进行交易。
     * 
     * @param index the index to place the trade
     *              进行交易的指数
     */
    default void operate(int index) {
        operate(index, NaN, NaN);
    }

    /**
     * Places a trade in the trading record.
     * * 在交易记录中进行交易。
     * 
     * @param index  the index to place the trade
     *               进行交易的指数
     *
     * @param price  the trade price
     *               交易价格
     *
     * @param amount the trade amount
     *               交易金额
     */
    void operate(int index, Num price, Num amount);

    /**
     * Places an entry trade in the trading record.
     * * 在交易记录中放置一个入场交易。
     * 
     * @param index the index to place the entry
     *              放置条目的索引
     *
     * @return true if the entry has been placed, false otherwise
     * 如果已放置条目，则为 true，否则为 false
     */
    default boolean enter(int index) {
        return enter(index, NaN, NaN);
    }

    /**
     * Places an entry trade in the trading record.
     * * 在交易记录中放置一个入场交易。
     * 
     * @param index  the index to place the entry
     *               放置条目的索引
     *
     * @param price  the trade price
     *               交易价格
     *
     * @param amount the trade amount
     *               交易金额
     *
     * @return true if the entry has been placed, false otherwise
     * 如果已放置条目，则为 true，否则为 false
     */
    boolean enter(int index, Num price, Num amount);

    /**
     * Places an exit trade in the trading record.
     * * 在交易记录中放置退出交易。
     * 
     * @param index the index to place the exit
     *              放置出口的索引
     *
     * @return true if the exit has been placed, false otherwise
     * @return 如果已放置出口，则返回 true，否则返回 false
     */
    default boolean exit(int index) {
        return exit(index, NaN, NaN);
    }

    /**
     * Places an exit trade in the trading record.
     * * 在交易记录中放置退出交易。
     * 
     * @param index  the index to place the exit
     *               放置出口的索引
     *
     * @param price  the trade price
     *               交易价格
     *
     * @param amount the trade amount
     *               交易金额
     *
     * @return true if the exit has been placed, false otherwise
     * @return 如果已放置出口，则返回 true，否则返回 false
     */
    boolean exit(int index, Num price, Num amount);

    /**
     * @return true if no position is open, false otherwise
     * * @return 如果没有空仓，则返回 true，否则返回 false
     */
    default boolean isClosed() {
        return !getCurrentPosition().isOpened();
    }

    /**
     * @return the recorded positions
     * * @return 记录的位置
     */
    List<Position> getPositions();

    /**
     * @return the number of recorded positions
     * * @return 记录的位置数
     */
    default int getPositionCount() {
        return getPositions().size();
    }

    /**
     * @return the current position
     * * @return 当前位置
     */
    Position getCurrentPosition();

    /**
     * @return the last position recorded
     * * @return 最后记录的位置
     */
    default Position getLastPosition() {
        List<Position> positions = getPositions();
        if (!positions.isEmpty()) {
            return positions.get(positions.size() - 1);
        }
        return null;
    }

    /**
     * @return the last trade recorded
     * * @return 最后记录的交易
     */
    Trade getLastTrade();

    /**
     * @param tradeType the type of the trade to get the last of
     *                  * @param tradeType 获取最后一笔交易的类型
     *
     * @return the last trade (of the provided type) recorded
     * * @return 记录的最后一笔交易（提供的类型）
     */
    Trade getLastTrade(TradeType tradeType);

    /**
     * @return the last entry trade recorded
     * * @return 记录的最后一次入场交易
     */
    Trade getLastEntry();

    /**
     * @return the last exit trade recorded
     * * @return 记录的最后退出交易
     */
    Trade getLastExit();
}
