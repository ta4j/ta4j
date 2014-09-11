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
package eu.verdelhan.ta4j;


/**
 * A trading strategy.
 * <p>
 * Parameter: an {@link Indicator indicator} or another {@link Strategy strategy}
 * <p>
 * Returns an {@link Operation operation} when giving an index.
 */
public interface Strategy {

    /**
     * @param trade a trade
     * @param index the index
     * @return true to recommend an operation, false otherwise (no recommendation)
     */
    boolean shouldOperate(Trade trade, int index);

    /**
     * @param index the index
     * @return true to recommend to enter (BUY {@link Operation operation}), false otherwise
     */
    boolean shouldEnter(int index);

    /**
     * @param index the index
     * @return true to recommend to exit (SELL {@link Operation operation}), false otherwise
     */
    boolean shouldExit(int index);

    /**
     * @param strategy another trading strategy
     * @return a strategy which is the AND combination of the current strategy with the provided one
     */
    Strategy and(Strategy strategy);

    /**
     * @param strategy another trading strategy
     * @return a strategy which is the OR combination of the current strategy with the provided one
     */
    Strategy or(Strategy strategy);

    /**
     * @return a strategy which operates in the opposite way of the current strategy
     */
    Strategy opposite();
}
