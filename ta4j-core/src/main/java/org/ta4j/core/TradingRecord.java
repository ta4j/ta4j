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

import org.ta4j.core.Pos.PosType;
import org.ta4j.core.num.Num;

/**
 * A history/record of a trading session.
 *
 * <p>
 * Holds the full trading record when running a {@link Strategy strategy}. It is
 * used to:
 * <ul>
 * <li>check to satisfaction of some trading rules (when running a strategy)
 * <li>analyze the performance of a trading strategy
 * </ul>
 */
public interface TradingRecord extends Serializable {

    /**
     * @return the entry type (BUY or SELL) of the first position in the trading
     *         session
     */
    PosType getStartingType();

    /**
     * @return the name of the TradingRecord
     */
    String getName();

    /**
     * Places a position in the trading record.
     * 
     * @param index the index to place the position
     */
    default void operate(int index) {
        operate(index, NaN, NaN);
    }

    /**
     * Places a position in the trading record.
     * 
     * @param index  the index to place the position
     * @param price  the exit price
     * @param amount the exit amount
     */
    void operate(int index, Num price, Num amount);

    /**
     * Places an entry position in the trading record.
     * 
     * @param index the index to place the entry position
     * @return true if the entry has been operated, false otherwise
     */
    default boolean enter(int index) {
        return enter(index, NaN, NaN);
    }

    /**
     * Places an entry position in the trading record.
     * 
     * @param index  the index to place the entry position
     * @param price  the entry price
     * @param amount the entry amount
     * @return true if the entry has been operated, false otherwise
     */
    boolean enter(int index, Num price, Num amount);

    /**
     * Places an exit position in the trading record.
     * 
     * @param index the index to place the exit position
     * @return true if the exit has been operated, false otherwise
     */
    default boolean exit(int index) {
        return exit(index, NaN, NaN);
    }

    /**
     * Places an exit position in the trading record.
     * 
     * @param index  the index to place the exit position
     * @param price  the exit price
     * @param amount the exit amount
     * @return true if the exit has been operated, false otherwise
     */
    boolean exit(int index, Num price, Num amount);

    /**
     * @return true if no position is open, false otherwise
     */
    default boolean isClosed() {
        return !getCurrentPair().isOpened();
    }

    /**
     * @return the recorded position pairs
     */
    List<PosPair> getPairs();

    /**
     * @return the number of recorded position pairs
     */
    default int getPairsCount() {
        return getPairs().size();
    }

    /**
     * @return the current position pair
     */
    PosPair getCurrentPair();

    /**
     * @return the last position pair recorded
     */
    default PosPair getLastPair() {
        List<PosPair> pairs = getPairs();
        if (!pairs.isEmpty()) {
            return pairs.get(pairs.size() - 1);
        }
        return null;
    }

    /**
     * @return the last position recorded
     */
    Pos getLastPosition();

    /**
     * @param positionType the type of the position to get the last of
     * @return the last position (of the provided type) recorded
     */
    Pos getLastPosition(PosType positionType);

    /**
     * @return the last entry position recorded
     */
    Pos getLastEntry();

    /**
     * @return the last exit position recorded
     */
    Pos getLastExit();
}
