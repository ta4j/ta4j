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

/**
 * A trading strategy.
 * 一种交易策略。
 *
 * A strategy is a pair of complementary {@link Rule rules}. It may recommend to enter or to exit. Recommendations are based respectively on the entry rule or on the exit rule.
 * * 策略是一对互补的{@link Rule rules}。 它可能会建议进入或退出。 建议分别基于进入规则或退出规则。
 */
public interface Strategy {

    /**
     * @return the name of the strategy
     * * @return 策略名称
     */
    String getName();

    /**
     * @return the entry rule
     * * @return 进入规则
     */
    Rule getEntryRule();

    /**
     * @return the exit rule
     * * @return 退出规则
     */
    Rule getExitRule();

    /**
     * @param strategy the other strategy
     *                 另一种策略
     * @return the AND combination of two {@link Strategy strategies}
     * * @return 两个 {@link 策略策略} 的 AND 组合
     */
    Strategy and(Strategy strategy);

    /**
     * @param strategy the other strategy
     *                 另一种策略
     *
     * @return the OR combination of two {@link Strategy strategies}
     * @return 两个 {@link 策略策略} 的 OR 组合
     */
    Strategy or(Strategy strategy);

    /**
     * @param name           the name of the strategy
     *                       策略名称
     *
     * @param strategy       the other strategy
     *                       另一种策略
     *
     * @param unstablePeriod number of bars that will be strip off for this strategy
     *                       * @param stablePeriod 将为此策略剥离的柱数
     *
     * @return the AND combination of two {@link Strategy strategies}
     * * @return 两个 {@link 策略策略} 的 AND 组合
     */
    Strategy and(String name, Strategy strategy, int unstablePeriod);

    /**
     * @param name           the name of the strategy
     *                       策略名称
     *
     * @param strategy       the other strategy
     *                       另一种策略
     *
     * @param unstablePeriod number of bars that will be strip off for this strategy
     *                            * @param stablePeriod 将为此策略剥离的柱数
     *
     * @return the OR combination of two {@link Strategy strategies}
     * @return 两个 {@link 策略策略} 的 OR 组合
     */
    Strategy or(String name, Strategy strategy, int unstablePeriod);

    /**
     * @return the opposite of the {@link Strategy strategy}
     *      * @return the opposite of the {@link Strategy strategy}
     */
    Strategy opposite();

    /**
     * @param unstablePeriod number of bars that will be strip off for this strategy
     *                       * @param stablePeriod 将为此策略剥离的柱数
     */
    void setUnstablePeriod(int unstablePeriod);

    /**
     * @return unstablePeriod number of bars that will be strip off for this  strategy
     * * @return stablePeriod 将为此策略剥离的柱数
     */
    int getUnstablePeriod();

    /**
     * @param index a bar index
     *              条形索引
     * @return true if this strategy is unstable at the provided index, false  otherwise (stable)
     * * @return 如果此策略在提供的索引处不稳定，则返回 true，否则返回 false（稳定）
     */
    boolean isUnstableAt(int index);

    /**
     * @param index         the bar index
     *                      条形索引
     *
     * @param tradingRecord the potentially needed trading history
     *                      可能需要的交易历史
     *
     * @return true to recommend a trade, false otherwise (no recommendation)
     * * @return true 推荐交易，否则为 false（不推荐）
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
     *              条形索引
     *
     * @return true to recommend to enter, false otherwise
     * @return true 推荐进入，否则返回 false
     */
    default boolean shouldEnter(int index) {
        return shouldEnter(index, null);
    }

    /**
     * @param index         the bar index
     *                      条形索引
     *
     * @param tradingRecord the potentially needed trading history
     *                      可能需要的交易历史
     *
     * @return true to recommend to enter, false otherwise
     * @return true 推荐进入，否则返回 false
     */
    default boolean shouldEnter(int index, TradingRecord tradingRecord) {
        return !isUnstableAt(index) && getEntryRule().isSatisfied(index, tradingRecord);
    }

    /**
     * @param index the bar index
     *              条形索引
     *
     * @return true to recommend to exit, false otherwise
     * true 建议退出，否则为 false
     */
    default boolean shouldExit(int index) {
        return shouldExit(index, null);
    }

    /**
     * @param index         the bar index
     *                      条形索引
     *
     * @param tradingRecord the potentially needed trading history
     *                      可能需要的交易历史
     *
     * @return true to recommend to exit, false otherwise
     * true 建议退出，否则为 false
     */
    default boolean shouldExit(int index, TradingRecord tradingRecord) {
        return !isUnstableAt(index) && getExitRule().isSatisfied(index, tradingRecord);
    }
}
