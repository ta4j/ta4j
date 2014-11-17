/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract trading {@link Strategy strategy}.
 * <p>
 */
public abstract class AbstractStrategy implements Strategy {

    /** The logger */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public boolean shouldOperate(Trade trade, int index) {
        if (trade.isNew()) {
            return shouldEnter(index);
        } else if (trade.isOpened()) {
            return shouldExit(index);
        }
        return false;
    }

    @Override
    public Strategy and(Strategy strategy) {
        return new AndStrategy(this, strategy);
    }

    @Override
    public Strategy or(Strategy strategy) {
        return new OrStrategy(this, strategy);
    }

    @Override
    public Strategy opposite() {
        return new OppositeStrategy(this);
    }

    /**
     * Traces the shouldEnter() method calls.
     * @param index the index
     * @param enter true if the strategy should enter, false otherwise
     */
    protected void traceEnter(int index, boolean enter) {
        log.trace("{}#shouldEnter({}): {}", getClass().getSimpleName(), index, enter);
    }

    /**
     * Traces the shouldExit() method calls.
     * @param index the index
     * @param exit true if the strategy should exit, false otherwise
     */
    protected void traceExit(int index, boolean exit) {
        log.trace("{}#shouldExit({}): {}", getClass().getSimpleName(), index, exit);
    }
}