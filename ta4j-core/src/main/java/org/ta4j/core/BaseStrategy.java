package org.ta4j.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Base implementation of a {@link Strategy}.
 */
public class BaseStrategy implements Strategy {

    /** The logger */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** The class name */
    private final String className = getClass().getSimpleName();

    /** Name of the strategy */
    private String name;

    /** The entry rule */
    private Rule entryRule;

    /** The exit rule */
    private Rule exitRule;

    /**
     * The unstable period (number of bars).<br>
     * During the unstable period of the strategy any order placement will be cancelled.<br>
     * I.e. no entry/exit signal will be fired before index == unstablePeriod.
     */
    private int unstablePeriod;

    /**
     * Constructor.
     * @param entryRule the entry rule
     * @param exitRule the exit rule
     */
    public BaseStrategy(Rule entryRule, Rule exitRule) {
        this(null, entryRule, exitRule, 0);
    }

     /**
     * Constructor.
     * @param entryRule the entry rule
     * @param exitRule the exit rule
     * @param unstablePeriod strategy will ignore possible signals at <code>index</code> < <code>unstablePeriod</code>
     */
    public BaseStrategy(Rule entryRule, Rule exitRule, int unstablePeriod) {
        this(null, entryRule, exitRule, unstablePeriod);
    }

    /**
     * Constructor.
     * @param name the name of the strategy
     * @param entryRule the entry rule
     * @param exitRule the exit rule
     */
    public BaseStrategy(String name, Rule entryRule, Rule exitRule) {
        this(name, entryRule, exitRule, 0);
    }

    /**
     * Constructor.
     * @param name the name of the strategy
     * @param entryRule the entry rule
     * @param exitRule the exit rule
     * @param unstablePeriod strategy will ignore possible signals at <code>index</code> < <code>unstablePeriod</code>
     */
    public BaseStrategy(String name, Rule entryRule, Rule exitRule, int unstablePeriod) {
        if (entryRule == null || exitRule == null) {
            throw new IllegalArgumentException("Rules cannot be null");
        }
        if (unstablePeriod < 0) {
        	throw new IllegalArgumentException("Unstable period bar count must be >= 0");
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
        return new BaseStrategy(name, entryRule.and(strategy.getEntryRule()), exitRule.and(strategy.getExitRule()), unstablePeriod);
    }

    @Override
    public Strategy or(String name, Strategy strategy, int unstablePeriod) {
        return new BaseStrategy(name, entryRule.or(strategy.getEntryRule()), exitRule.or(strategy.getExitRule()), unstablePeriod);
    }

    /**
     * Traces the shouldEnter() method calls.
     * @param index the bar index
     * @param enter true if the strategy should enter, false otherwise
     */
    protected void traceShouldEnter(int index, boolean enter) {
        log.trace(">>> {}#shouldEnter({}): {}", className, index, enter);
    }

    /**
     * Traces the shouldExit() method calls.
     * @param index the bar index
     * @param exit true if the strategy should exit, false otherwise
     */
    protected void traceShouldExit(int index, boolean exit) {
        log.trace(">>> {}#shouldExit({}): {}", className, index, exit);
    }
}
