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
 * Combination of a BUY {@link Strategy strategy} and a SELL {@link Strategy strategy}.
 * <p>
 * Enter: according to the provided BUY {@link Strategy strategy}<br>
 * Exit: according to the provided SELL {@link Strategy strategy}
 */
public class CombinedBuyAndSellStrategy extends AbstractStrategy {

    /** Buy strategy */
    private Strategy buyStrategy;
    /** Sell strategy */
    private Strategy sellStrategy;

    /**
     * Constructor.
     * @param buyStrategy the buy strategy
     * @param sellStrategy the sell strategy
     */
    public CombinedBuyAndSellStrategy(Strategy buyStrategy, Strategy sellStrategy) {
        this.buyStrategy = buyStrategy;
        this.sellStrategy = sellStrategy;
    }

    @Override
    public boolean shouldEnter(int index) {
        boolean enter = buyStrategy.shouldEnter(index);
        traceEnter(index, enter);
        return enter;
    }

    @Override
    public boolean shouldExit(int index) {
        boolean exit = sellStrategy.shouldExit(index);
        traceExit(index, exit);
        return exit;
    }

    @Override
    public String toString() {
        return String.format("Combined strategy using buy strategy %s and sell strategy %s", buyStrategy, sellStrategy);
    }
}
