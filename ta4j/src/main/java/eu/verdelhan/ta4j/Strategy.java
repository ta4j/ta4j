/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A trading strategy.
 * <p>
 * A strategy is a pair of complementary {@link Rule rules}. It may recommend to enter or to exit.
 * Recommendations are based respectively on the entry rule or on the exit rule.
 */
public class Strategy {

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
    public Strategy(Rule entryRule, Rule exitRule) {
        if (entryRule == null || exitRule == null) {
            throw new IllegalArgumentException("Rules cannot be null");
        }
        this.entryRule = entryRule;
        this.exitRule = exitRule;
    }
    
    /**
     * @param index a tick index
     * @return true if this strategy is unstable at the provided index, false otherwise (stable)
     */
    public boolean isUnstableAt(int index) {
        return index < unstablePeriod;
    }
    
    /**
     * @param unstablePeriod number of ticks that will be strip off for this strategy
     */
    public void setUnstablePeriod(int unstablePeriod) {
        this.unstablePeriod = unstablePeriod;
    }
    
    /**
     * @param index the tick index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend an order, false otherwise (no recommendation)
     */
    public boolean shouldOperate(int index, TradingRecord tradingRecord) {
        Trade trade = tradingRecord.getCurrentTrade();
        if (trade.isNew()) {
            return shouldEnter(index, tradingRecord);
        } else if (trade.isOpened()) {
            return shouldExit(index, tradingRecord);
        }
        return false;
    }

    /**
     * @param index the tick index
     * @return true to recommend to enter, false otherwise
     */
    public boolean shouldEnter(int index) {
        return shouldEnter(index, null);
    }

    /**
     * @param index the tick index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend to enter, false otherwise
     */
    public boolean shouldEnter(int index, TradingRecord tradingRecord) {
        if (isUnstableAt(index)) {
            return false;
        }
        final boolean enter = entryRule.isSatisfied(index, tradingRecord);
        traceShouldEnter(index, enter);
        return enter;
    }

    /**
     * @param index the tick index
     * @return true to recommend to exit, false otherwise
     */
    public boolean shouldExit(int index) {
        return shouldExit(index, null);
    }

    /**
     * @param index the tick index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend to exit, false otherwise
     */
    public boolean shouldExit(int index, TradingRecord tradingRecord) {
        if (isUnstableAt(index)) {
            return false;
        }
        final boolean exit = exitRule.isSatisfied(index, tradingRecord);
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
