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

/**
 * Resistance strategy.
 * <p>
 * Enter: according to the provided {@link Strategy strategy}<br>
 * Exit: when the {@link Indicator indicator} value is greater than or equal to the resistance threshold
 */
public class ResistanceStrategy extends AbstractStrategy {

    private final Strategy strategy;

    private final Indicator<? extends Number> indicator;

    private double resistance;

    /**
     * Constructor.
     * @param indicator the indicator
     * @param strategy the strategie
     * @param resistance the resistance threshold
     */
    public ResistanceStrategy(Indicator<? extends Number> indicator, Strategy strategy, double resistance) {
        this.strategy = strategy;
        this.resistance = resistance;
        this.indicator = indicator;
    }

    @Override
    public boolean shouldEnter(int index) {
        return strategy.shouldEnter(index);
    }

    @Override
    public boolean shouldExit(int index) {
        if (indicator.getValue(index).doubleValue() >= resistance) {
            return true;
        }
        return strategy.shouldExit(index);
    }

    @Override
    public String toString() {
        return String.format("%s resistance: %d strategy: %s", this.getClass().getSimpleName(), resistance, strategy);
    }
}
