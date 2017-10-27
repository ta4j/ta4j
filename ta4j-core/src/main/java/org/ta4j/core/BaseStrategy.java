/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Base implementation of a {@link Strategy}.
 */
public class BaseStrategy implements Strategy {

    /** The logger */
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    /** The entry rule */
    private Rule entryRule;
    
    /** The exit rule */
    private Rule exitRule;

    /**
     * The unstable period (number of ticks).<br>
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
        this(entryRule, exitRule, 0);
    }
    
    /**
     * Constructor.
     * @param entryRule the entry rule
     * @param exitRule the exit rule
     */
    public BaseStrategy(Rule entryRule, Rule exitRule, int unstablePeriod) {
        if (entryRule == null || exitRule == null) {
            throw new IllegalArgumentException("Rules cannot be null");
        }
        if (unstablePeriod < 0) {
        	throw new IllegalArgumentException("Unstable period tick count must be >= 0");
        }
        this.entryRule = entryRule;
        this.exitRule = exitRule;
        this.unstablePeriod = unstablePeriod;
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

    /**
     * Traces the shouldEnter() method calls.
     * @param index the tick index
     * @param enter true if the strategy should enter, false otherwise
     */
    protected void traceShouldEnter(int index, boolean enter) {
        log.trace(">>> {}#shouldEnter({}): {}", getClass().getSimpleName(), index, enter);
    }

    /**
     * Traces the shouldExit() method calls.
     * @param index the tick index
     * @param exit true if the strategy should exit, false otherwise
     */
    protected void traceShouldExit(int index, boolean exit) {
        log.trace(">>> {}#shouldExit({}): {}", getClass().getSimpleName(), index, exit);
    }
}
