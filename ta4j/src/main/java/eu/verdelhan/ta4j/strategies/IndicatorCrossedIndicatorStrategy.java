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
import eu.verdelhan.ta4j.indicators.helpers.CrossIndicator;

/**
 * Indicator crossed indicator strategy.
 * <p>
 * Enter: when the upper {@link Indicator indicator} is above the lower one then cross it<br>
 * Exit: when the lower {@link Indicator indicator} is above the upper one then cross it
 */
public class IndicatorCrossedIndicatorStrategy extends AbstractStrategy {

    private final Indicator<Boolean> crossUp;

    private final Indicator<Boolean> crossDown;

    private Indicator<? extends Number> upper;

    private Indicator<? extends Number> lower;

    /**
     * Constructor.
     * @param upper the upper indicator
     * @param lower the lower indicator
     */
    public IndicatorCrossedIndicatorStrategy(Indicator<? extends Number> upper, Indicator<? extends Number> lower) {
        this.upper = upper;
        this.lower = lower;
        crossUp = new CrossIndicator(upper, lower);
        crossDown = new CrossIndicator(lower, upper);
    }

    @Override
    public boolean shouldEnter(int index) {
        return crossUp.getValue(index);
    }

    @Override
    public boolean shouldExit(int index) {
        return crossDown.getValue(index);
    }

    @Override
    public String toString() {
        return String.format("Cross %s over %s", upper, lower);
    }
}
