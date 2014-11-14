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

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TADecimal;

/**
 * Minimum value starter strategy.
 * <p>
 * Enter: when an {@link Indicator indicator}'s value exceed a threshold<br>
 * Exit: according to the provided {@link Strategy strategy}
 */
public class MinValueStarterStrategy extends AbstractStrategy {

    /** Strategy */
    private Strategy strategy;
    /** Indicator */
    private Indicator<? extends TADecimal> indicator;
    /** Starting threshold */
    private TADecimal start;

    /**
     * Constructor.
     * @param indicator the indicator
     * @param strategy the strategy
     * @param start the starting threshold
     */
    public MinValueStarterStrategy(Indicator<? extends TADecimal> indicator, Strategy strategy, double start) {
        this.indicator = indicator;
        this.strategy = strategy;
        this.start = TADecimal.valueOf(start);
    }

    @Override
    public boolean shouldEnter(int index) {
        boolean enter = indicator.getValue(index).isGreaterThan(start);
        traceEnter(index, enter);
        return enter;
    }

    @Override
    public boolean shouldExit(int index) {
        boolean exit = strategy.shouldExit(index);
        traceExit(index, exit);
        return exit;
    }

    @Override
    public String toString() {
        return String.format("%s start: %f", this.getClass().getSimpleName(), start);
    }
}
