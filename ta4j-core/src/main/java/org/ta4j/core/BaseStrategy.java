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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation of a {@link Strategy}.
 */
public class BaseStrategy implements Strategy {

    /** The logger. */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** The class name. */
    private final String className = getClass().getSimpleName();

    /** The name of the strategy. */
    private final String name;

    /** The entry rule. */
    private final Rule entryRule;

    /** The exit rule. */
    private final Rule exitRule;

    /**
     * The number of first bars in a bar series that this strategy ignores. During
     * the unstable bars of the strategy, any trade placement will be canceled i.e.
     * no entry/exit signal will be triggered before {@code index == unstableBars}.
     */
    private int unstableBars;

    /**
     * Constructor.
     *
     * @param entryRule the entry rule
     * @param exitRule  the exit rule
     */
    public BaseStrategy(Rule entryRule, Rule exitRule) {
        this(null, entryRule, exitRule, 0);
    }

    /**
     * Constructor.
     *
     * @param entryRule    the entry rule
     * @param exitRule     the exit rule
     * @param unstableBars strategy will ignore possible signals at
     *                     {@code index < unstableBars}
     */
    public BaseStrategy(Rule entryRule, Rule exitRule, int unstableBars) {
        this(null, entryRule, exitRule, unstableBars);
    }

    /**
     * Constructor.
     *
     * @param name      the name of the strategy
     * @param entryRule the entry rule
     * @param exitRule  the exit rule
     */
    public BaseStrategy(String name, Rule entryRule, Rule exitRule) {
        this(name, entryRule, exitRule, 0);
    }

    /**
     * Constructor.
     *
     * @param name         the name of the strategy
     * @param entryRule    the entry rule
     * @param exitRule     the exit rule
     * @param unstableBars strategy will ignore possible signals at
     *                     {@code index < unstableBars}
     * @throws IllegalArgumentException if entryRule or exitRule is null
     */
    public BaseStrategy(String name, Rule entryRule, Rule exitRule, int unstableBars) {
        if (entryRule == null || exitRule == null) {
            throw new IllegalArgumentException("Rules cannot be null");
        }
        if (unstableBars < 0) {
            throw new IllegalArgumentException("Unstable bars must be >= 0");
        }
        this.name = name;
        this.entryRule = entryRule;
        this.exitRule = exitRule;
        this.unstableBars = unstableBars;
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
    public int getUnstableBars() {
        return unstableBars;
    }

    @Override
    public void setUnstableBars(int unstableBars) {
        this.unstableBars = unstableBars;
    }

    @Override
    public boolean isUnstableAt(int index) {
        return index < unstableBars;
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
        int unstable = Math.max(unstableBars, strategy.getUnstableBars());
        return and(andName, strategy, unstable);
    }

    @Override
    public Strategy or(Strategy strategy) {
        String orName = "or(" + name + "," + strategy.getName() + ")";
        int unstable = Math.max(unstableBars, strategy.getUnstableBars());
        return or(orName, strategy, unstable);
    }

    @Override
    public Strategy opposite() {
        return new BaseStrategy("opposite(" + name + ")", exitRule, entryRule, unstableBars);
    }

    @Override
    public Strategy and(String name, Strategy strategy, int unstableBars) {
        return new BaseStrategy(name, entryRule.and(strategy.getEntryRule()), exitRule.and(strategy.getExitRule()),
                unstableBars);
    }

    @Override
    public Strategy or(String name, Strategy strategy, int unstableBars) {
        return new BaseStrategy(name, entryRule.or(strategy.getEntryRule()), exitRule.or(strategy.getExitRule()),
                unstableBars);
    }

    /**
     * Traces the {@code shouldEnter()} method calls.
     *
     * @param index the bar index
     * @param enter true if the strategy should enter, false otherwise
     */
    protected void traceShouldEnter(int index, boolean enter) {
        if (log.isTraceEnabled()) {
            log.trace(">>> {}#shouldEnter({}): {}", className, index, enter);
        }
    }

    /**
     * Traces the {@code shouldExit()} method calls.
     *
     * @param index the bar index
     * @param exit  true if the strategy should exit, false otherwise
     */
    protected void traceShouldExit(int index, boolean exit) {
        if (log.isTraceEnabled()) {
            log.trace(">>> {}#shouldExit({}): {}", className, index, exit);
        }
    }
}
