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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation of a {@link Strategy}.
 * * {@link Strategy} 的基本实现。
 */
public class BaseStrategy implements Strategy {

    /** The logger
     * 记录器 */
    protected final transient Logger log = LoggerFactory.getLogger(getClass());

    /** The class name
     * 班级名称 */
    private final String className = getClass().getSimpleName();

    /** Name of the strategy
     * 策略名称*/
    private String name;

    /** The entry rule
     * 进入规则 */
    private Rule entryRule;

    /** The exit rule
     * 退出规则 */
    private Rule exitRule;

    /**
     * The unstable period (number of bars).<br>
     * 不稳定时期（柱数）。<br>
     *
     * During the unstable period of the strategy any trade placement will be cancelled.<br>
     * * 在策略不稳定期间，任何交易将被取消。<br>
     *
     * I.e. no entry/exit signal will be fired before index == unstablePeriod.
     * * IE。 在 index == stablePeriod 之前不会触发任何进入/退出信号。
     */
    private int unstablePeriod;

    /**
     * Constructor.
     * 
     * @param entryRule the entry rule
     *                  进入规则
     *
     * @param exitRule  the exit rule
     *                  退出规则
     */
    public BaseStrategy(Rule entryRule, Rule exitRule) {
        this(null, entryRule, exitRule, 0);
    }

    /**
     * Constructor.
     * 
     * @param entryRule      the entry rule
     *                       进入规则
     *
     * @param exitRule       the exit rule
     *                       退出规则
     *
     * @param unstablePeriod strategy will ignore possible signals at   <code>index</code> < <code>unstablePeriod</code>
     *                       * @param stablePeriod 策略将忽略 <code>index</code> < <code>unstablePeriod</code> 的可能信号
     */
    public BaseStrategy(Rule entryRule, Rule exitRule, int unstablePeriod) {
        this(null, entryRule, exitRule, unstablePeriod);
    }

    /**
     * Constructor.
     * 
     * @param name      the name of the strategy
     *                  策略名称
     *
     * @param entryRule the entry rule
     *                  进入规则
     *
     * @param exitRule  the exit rule
     *                  退出规则
     */
    public BaseStrategy(String name, Rule entryRule, Rule exitRule) {
        this(name, entryRule, exitRule, 0);
    }

    /**
     * Constructor.
     * 
     * @param name           the name of the strategy
     *                       策略名称
     *
     * @param entryRule      the entry rule
     *                       进入规则
     *
     * @param exitRule       the exit rule
     *                       退出规则
     *
     * @param unstablePeriod strategy will ignore possible signals at <code>index</code> < <code>unstablePeriod</code>
     *                       * @param stablePeriod 策略将忽略 <code>index</code> < <code>unstablePeriod</code> 的可能信号
     */
    public BaseStrategy(String name, Rule entryRule, Rule exitRule, int unstablePeriod) {
        if (entryRule == null || exitRule == null) {
            throw new IllegalArgumentException("Rules cannot be null 规则不能为空");
        }
        if (unstablePeriod < 0) {
            throw new IllegalArgumentException("Unstable period bar count must be >= 0 不稳定周期柱数必须 >= 0");
        }
        this.name = name;
        this.entryRule = entryRule;
        this.exitRule = exitRule;
        this.unstablePeriod = unstablePeriod;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Rule getEntryRule() {
        return entryRule;
    }

    @Override
    public Rule getExitRule() {
        return exitRule;
    }

    @Override
    public int getUnstablePeriod() {
        return unstablePeriod;
    }

    @Override
    public void setUnstablePeriod(int unstablePeriod) {
        this.unstablePeriod = unstablePeriod;
    }

    @Override
    public boolean isUnstableAt(int index) {
        return index < unstablePeriod;
    }

    @Override
    public boolean shouldEnter(int index, TradingRecord tradingRecord) {
        boolean enter = Strategy.super.shouldEnter(index, tradingRecord);
        traceShouldEnter(index, enter);
        return enter;
    }

    @Override
    public boolean shouldExit(int index, TradingRecord tradingRecord) {
        boolean exit = Strategy.super.shouldExit(index, tradingRecord);
        traceShouldExit(index, exit);
        return exit;
    }

    @Override
    public Strategy and(Strategy strategy) {
        String andName = "and(" + name + "," + strategy.getName() + ")";
        int unstable = Math.max(unstablePeriod, strategy.getUnstablePeriod());
        return and(andName, strategy, unstable);
    }

    @Override
    public Strategy or(Strategy strategy) {
        String orName = "or(" + name + "," + strategy.getName() + ")";
        int unstable = Math.max(unstablePeriod, strategy.getUnstablePeriod());
        return or(orName, strategy, unstable);
    }

    @Override
    public Strategy opposite() {
        return new BaseStrategy("opposite(" + name + ")", exitRule, entryRule, unstablePeriod);
    }

    @Override
    public Strategy and(String name, Strategy strategy, int unstablePeriod) {
        return new BaseStrategy(name, entryRule.and(strategy.getEntryRule()), exitRule.and(strategy.getExitRule()),
                unstablePeriod);
    }

    @Override
    public Strategy or(String name, Strategy strategy, int unstablePeriod) {
        return new BaseStrategy(name, entryRule.or(strategy.getEntryRule()), exitRule.or(strategy.getExitRule()),
                unstablePeriod);
    }

    /**
     * Traces the shouldEnter() method calls.
     * 跟踪 shouldEnter() 方法调用。
     * 
     * @param index the bar index
     *              条形索引
     *
     * @param enter true if the strategy should enter, false otherwise
     *              如果策略应该进入，则为 true，否则为 false
     */
    protected void traceShouldEnter(int index, boolean enter) {
        log.trace(">>> {}#shouldEnter 应该输入({}): {}", className, index, enter);
    }

    /**
     * Traces the shouldExit() method calls.
     * 跟踪 shouldExit() 方法调用。
     * 
     * @param index the bar index
     *              条形索引
     *
     * @param exit  true if the strategy should exit, false otherwise
     *              如果策略应该退出，则为 true，否则为 false
     */
    protected void traceShouldExit(int index, boolean exit) {
        log.trace(">>> {}#shouldExit 应该退出({}): {}", className, index, exit);
    }
}
