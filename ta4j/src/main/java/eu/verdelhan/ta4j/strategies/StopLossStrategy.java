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

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TADecimal;

/**
 * Stop loss strategy.
 * <p>
 * Enter: according to the provided {@link Strategy strategy}<br>
 * Exit: if the loss threshold (in %) has been reached with the provided {@link Indicator indicator}, or according to the provided {@link Strategy strategy}
 */
public class StopLossStrategy extends AbstractStrategy {

    private Strategy strategy;

    private TADecimal loss;

    private Indicator<? extends TADecimal> indicator;

    private TADecimal value;

    /**
     * Constructor.
     * @param indicator the indicator
     * @param strategy the strategy
     * @param loss the loss threshold (in %)
     */
    public StopLossStrategy(Indicator<? extends TADecimal> indicator, Strategy strategy, int loss) {
        this.strategy = strategy;
        this.loss = TADecimal.valueOf(loss);
        this.indicator = indicator;
    }

    @Override
    public boolean shouldEnter(int index) {
        boolean enter = strategy.shouldEnter(index);
        if (enter) {
            value = indicator.getValue(index);
        }
        traceEnter(index, enter);
        return enter;
    }

    @Override
    public boolean shouldExit(int index) {
        boolean exit = (value.minus(value.multipliedBy(loss.dividedBy(TADecimal.HUNDRED)))).isGreaterThan(indicator.getValue(index))
                || strategy.shouldExit(index);
        traceExit(index, exit);
        return exit;
    }

    @Override
    public String toString() {
        return String.format("%s stoper: %s over %s", this.getClass().getSimpleName(), "" + loss.toDouble(), strategy);
    }
}
