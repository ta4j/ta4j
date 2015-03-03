/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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


/**
 * A trading strategy.
 * <p>
 * Parameter: an {@link Indicator indicator} or another {@link Strategy strategy}
 * <p>
 * Returns an {@link Order order} when giving an index.
 */
public class Strategy {

    private Rule entryRule;
    
    private Rule exitRule;

    public Strategy() {
    }

    public Strategy(Rule entryRule, Rule exitRule) {
        this.entryRule = entryRule;
        this.exitRule = exitRule;
    }
    
    public void setEntryRule(Rule entryRule) {
        this.entryRule = entryRule;
    }

    public void setExitRule(Rule exitRule) {
        this.exitRule = exitRule;
    }
    
    /**
     * @param index the index
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
     * @param index the index
     * @return true to recommend to enter, false otherwise
     */
    public boolean shouldEnter(int index) {
        return shouldEnter(index, null);
    }

    /**
     * @param index the index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend to enter, false otherwise
     */
    public boolean shouldEnter(int index, TradingRecord tradingRecord) {
        return entryRule.isSatisfied(index, tradingRecord);
    }

    /**
     * @param index the index
     * @return true to recommend to exit, false otherwise
     */
    public boolean shouldExit(int index) {
        return shouldExit(index, null);
    }

    /**
     * @param index the index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend to exit, false otherwise
     */
    public boolean shouldExit(int index, TradingRecord tradingRecord) {
        return exitRule.isSatisfied(index, tradingRecord);
    }
}
