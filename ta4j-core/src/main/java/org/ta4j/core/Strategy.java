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

/**
 * A {@code Strategy} (also called "trading strategy") is a pair of
 * complementary (entry and exit) {@link Rule rules}. It may recommend to enter
 * or to exit. Recommendations are based respectively on the entry rule or on
 * the exit rule.
 */
public interface Strategy {

    /**
     * @return the name of the strategy
     */
    String getName();

    /**
     * @return the entry rule
     */
    Rule getEntryRule();

    /**
     * @return the exit rule
     */
    Rule getExitRule();

    /**
     * @param strategy the other strategy
     * @return the AND combination of two {@link Strategy strategies}
     */
    Strategy and(Strategy strategy);

    /**
     * @param strategy the other strategy
     * @return the OR combination of two {@link Strategy strategies}
     */
    Strategy or(Strategy strategy);

    /**
     * @param name         the name of the strategy
     * @param strategy     the other strategy
     * @param unstableBars the number of first bars in a bar series that this
     *                     strategy ignores
     * @return the AND combination of two {@link Strategy strategies}
     */
    Strategy and(String name, Strategy strategy, int unstableBars);

    /**
     * @param name         the name of the strategy
     * @param strategy     the other strategy
     * @param unstableBars the number of first bars in a bar series that this
     *                     strategy ignores
     * @return the OR combination of two {@link Strategy strategies}
     */
    Strategy or(String name, Strategy strategy, int unstableBars);

    /**
     * @return the opposite of the {@link Strategy strategy}
     */
    Strategy opposite();

    /**
     * @param unstableBars the number of first bars in a bar series that this
     *                     strategy ignores
     */
    void setUnstableBars(int unstableBars);

    /**
     * @return unstableBars the number of first bars in a bar series that this
     *         strategy ignores
     */
    int getUnstableBars();

    /**
     * @param index a bar index
     * @return true if this strategy is unstable at the provided index, false
     *         otherwise (stable)
     */
    boolean isUnstableAt(int index);

    /**
     * @param index         the bar index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend a trade, false otherwise (no recommendation)
     */
    default boolean shouldOperate(int index, TradingRecord tradingRecord) {
        Position position = tradingRecord.getCurrentPosition();
        if (position.isNew()) {
            return shouldEnter(index, tradingRecord);
        } else if (position.isOpened()) {
            return shouldExit(index, tradingRecord);
        }
        return false;
    }

    /**
     * @param index the bar index
     * @return true to recommend to enter, false otherwise
     */
    default boolean shouldEnter(int index) {
        return shouldEnter(index, null);
    }

    /**
     * @param index         the bar index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend to enter, false otherwise
     */
    default boolean shouldEnter(int index, TradingRecord tradingRecord) {
        return !isUnstableAt(index) && getEntryRule().isSatisfied(index, tradingRecord);
    }

    /**
     * @param index the bar index
     * @return true to recommend to exit, false otherwise
     */
    default boolean shouldExit(int index) {
        return shouldExit(index, null);
    }

    /**
     * @param index         the bar index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend to exit, false otherwise
     */
    default boolean shouldExit(int index, TradingRecord tradingRecord) {
        return !isUnstableAt(index) && getExitRule().isSatisfied(index, tradingRecord);
    }
}
