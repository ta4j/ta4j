/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Trade.TradeType;

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

    /** The entry trade type for this strategy. */
    private final TradeType startingType;

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
        this(null, entryRule, exitRule, 0, TradeType.BUY);
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
        this(null, entryRule, exitRule, unstableBars, TradeType.BUY);
    }

    /**
     * Constructor.
     *
     * @param entryRule    the entry rule
     * @param exitRule     the exit rule
     * @param startingType the entry trade type
     * @since 0.22.2
     */
    public BaseStrategy(Rule entryRule, Rule exitRule, TradeType startingType) {
        this(null, entryRule, exitRule, 0, startingType);
    }

    /**
     * Constructor.
     *
     * @param entryRule    the entry rule
     * @param exitRule     the exit rule
     * @param unstableBars strategy will ignore possible signals at
     *                     {@code index < unstableBars}
     * @param startingType the entry trade type
     * @since 0.22.2
     */
    public BaseStrategy(Rule entryRule, Rule exitRule, int unstableBars, TradeType startingType) {
        this(null, entryRule, exitRule, unstableBars, startingType);
    }

    /**
     * Constructor.
     *
     * @param name      the name of the strategy
     * @param entryRule the entry rule
     * @param exitRule  the exit rule
     */
    public BaseStrategy(String name, Rule entryRule, Rule exitRule) {
        this(name, entryRule, exitRule, 0, TradeType.BUY);
    }

    /**
     * Constructor.
     *
     * @param name         the name of the strategy
     * @param entryRule    the entry rule
     * @param exitRule     the exit rule
     * @param startingType the entry trade type
     * @since 0.22.2
     */
    public BaseStrategy(String name, Rule entryRule, Rule exitRule, TradeType startingType) {
        this(name, entryRule, exitRule, 0, startingType);
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
        this(name, entryRule, exitRule, unstableBars, TradeType.BUY);
    }

    /**
     * Constructor.
     *
     * @param name         the name of the strategy
     * @param entryRule    the entry rule
     * @param exitRule     the exit rule
     * @param unstableBars strategy will ignore possible signals at
     *                     {@code index < unstableBars}
     * @param startingType the entry trade type
     * @throws IllegalArgumentException if entryRule or exitRule is null
     * @since 0.22.2
     */
    public BaseStrategy(String name, Rule entryRule, Rule exitRule, int unstableBars, TradeType startingType) {
        if (entryRule == null || exitRule == null) {
            throw new IllegalArgumentException("Rules cannot be null");
        }
        if (unstableBars < 0) {
            throw new IllegalArgumentException("Unstable bars must be >= 0");
        }
        if (startingType == null) {
            throw new IllegalArgumentException("Starting type cannot be null");
        }
        this.name = name;
        this.entryRule = entryRule;
        this.exitRule = exitRule;
        this.unstableBars = unstableBars;
        this.startingType = startingType;
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
    public TradeType getStartingType() {
        return startingType;
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
        return new BaseStrategy("opposite(" + name + ")", exitRule, entryRule, unstableBars, startingType);
    }

    @Override
    public Strategy and(String name, Strategy strategy, int unstableBars) {
        return new BaseStrategy(name, entryRule.and(strategy.getEntryRule()), exitRule.and(strategy.getExitRule()),
                unstableBars, getStartingType());
    }

    @Override
    public Strategy or(String name, Strategy strategy, int unstableBars) {
        return new BaseStrategy(name, entryRule.or(strategy.getEntryRule()), exitRule.or(strategy.getExitRule()),
                unstableBars, getStartingType());
    }

    /**
     * Returns the display name to use in trace logs. Uses the configured name if
     * set, otherwise falls back to the class name.
     *
     * @return display name for tracing
     */
    protected String getTraceDisplayName() {
        return name != null ? name : className;
    }

    /**
     * Traces the {@code shouldEnter()} method calls.
     *
     * @param index the bar index
     * @param enter true if the strategy should enter, false otherwise
     */
    protected void traceShouldEnter(int index, boolean enter) {
        if (log.isTraceEnabled()) {
            log.trace(">>> {}#shouldEnter({}): {}", getTraceDisplayName(), index, enter);
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
            log.trace(">>> {}#shouldExit({}): {}", getTraceDisplayName(), index, exit);
        }
    }
}
