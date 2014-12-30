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
package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.Strategy;

/**
 * {@link Strategy} which waits for a number of {@link Tick ticks} between enter and exit.
 * <p>
 * Enter: according to the provided {@link Strategy strategy}<br>
 * Exit: after a number of ticks, and then, according to the provided {@link Strategy strategy}
 */
public class NotSoFastStrategy extends AbstractStrategy {

    /** Strategy */
    private Strategy strategy;
    /** The number of ticks before exit */
    private int numberOfTicks;
    /** The tick index of the enter */
    private int enterTickIndex;

    /**
     * Constructor.
     * @param strategy a strategy
     * @param numberOfTicks a number of ticks to wait
     */
    public NotSoFastStrategy(Strategy strategy, int numberOfTicks) {
        this.strategy = strategy;
        this.numberOfTicks = numberOfTicks;
    }

    @Override
    public boolean shouldEnter(int index) {
        boolean enter = strategy.shouldEnter(index);
        if (enter) {
            enterTickIndex = index;
        }
        traceEnter(index, enter);
        return enter;
    }

    @Override
    public boolean shouldExit(int index) {
        boolean exit = ((index - enterTickIndex) > numberOfTicks) && strategy.shouldExit(index);
        traceExit(index, exit);
        return exit;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " over strategy: " + strategy + " number of ticks: "
                + numberOfTicks;
    }
}
