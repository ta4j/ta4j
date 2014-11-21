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

/**
 * Combination of an entry {@link Strategy strategy} and an exit {@link Strategy strategy}.
 * <p>
 * Enter: according to the provided entry {@link Strategy strategy}<br>
 * Exit: according to the provided exit {@link Strategy strategy}
 */
public class CombinedEntryAndExitStrategy extends AbstractStrategy {

    /** Entry strategy */
    private Strategy entryStrategy;
    /** Exit strategy */
    private Strategy exitStrategy;

    /**
     * Constructor.
     * @param entryStrategy the entry strategy
     * @param exitStrategy the exit strategy
     */
    public CombinedEntryAndExitStrategy(Strategy entryStrategy, Strategy exitStrategy) {
        this.entryStrategy = entryStrategy;
        this.exitStrategy = exitStrategy;
    }

    @Override
    public boolean shouldEnter(int index) {
        boolean enter = entryStrategy.shouldEnter(index);
        traceEnter(index, enter);
        return enter;
    }

    @Override
    public boolean shouldExit(int index) {
        boolean exit = exitStrategy.shouldExit(index);
        traceExit(index, exit);
        return exit;
    }

    @Override
    public String toString() {
        return String.format("Combined strategy using entry strategy %s and exit strategy %s", entryStrategy, exitStrategy);
    }
}
