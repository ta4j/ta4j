/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
 * authors (see AUTHORS)
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
package org.ta4j.core;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.ta4j.core.num.Num;

/**
 * Indicator over a {@link BarSeries bar series}.
 * 
 * <p>
 * Returns a value of type <b>T</b> for each index of the bar series.
 *
 * @param <T> the type of the returned value (Double, Boolean, etc.)
 */
public interface Indicator<T> {

    /**
     * @param index the bar index
     * @return the value of the indicator
     */
    T getValue(int index);

    /**
     * @return the number of bars up to which the indicator calculates wrong values
     */
    int getUnstableBars();

    /**
     * @return the related bar series
     */
    BarSeries getBarSeries();

    /**
     * @return the Num of 0
     */
    default Num zero() {
        return getBarSeries().zero();
    }

    /**
     * @return the Num of 1
     */
    default Num one() {
        return getBarSeries().one();
    }

    /**
     * @return the Num of 100
     */
    default Num hundred() {
        return getBarSeries().hundred();
    }

    /**
     * @return the {@link Num Num extending class} for the given {@link Number}
     */
    default Num numOf(Number number) {
        return getBarSeries().numOf(number);
    }

    /**
     * @return all values from {@code this} Indicator over {@link #getBarSeries()}
     *         as a Stream
     */
    default Stream<T> stream() {
        return IntStream.range(getBarSeries().getBeginIndex(), getBarSeries().getEndIndex() + 1)
                .mapToObj(this::getValue);
    }

    /**
     * Returns all values of an {@link Indicator} within the given {@code index} and
     * {@code barCount} as an array of Doubles. The returned doubles could have a
     * minor loss of precision, if {@link Indicator} was based on {@link Num Num}.
     *
     * @param ref      the indicator
     * @param index    the index
     * @param barCount the barCount
     * @return array of Doubles within {@code index} and {@code barCount}
     */
    static Double[] toDouble(Indicator<Num> ref, int index, int barCount) {
        int startIndex = Math.max(0, index - barCount + 1);
        return IntStream.range(startIndex, startIndex + barCount)
                .mapToObj(ref::getValue)
                .map(Num::doubleValue)
                .toArray(Double[]::new);
    }

}
