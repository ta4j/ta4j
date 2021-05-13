/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
 * Indicator over a {@link BarSeries bar series}. <p/p> For each index of the
 * bar series, returns a value of type <b>T</b>.
 *
 * @param <T> the type of returned value (Double, Boolean, etc.)
 */
public interface Indicator<T> {

    /**
     * @param index the bar index
     * @return the value of the indicator
     */
    T getValue(int index);

    /**
     * @return the related bar series
     */
    BarSeries getBarSeries();

    /**
     * @return the {@link Num Num extending class} for the given {@link Number}
     */
    Num numOf(Number number);

    /**
     * Returns all values from an {@link Indicator} as an array of Doubles. The
     * returned doubles could have a minor loss of precise, if {@link Indicator} was
     * based on {@link Num Num}.
     *
     * @param ref      the indicator
     * @param index    the index
     * @param barCount the barCount
     * @return array of Doubles within the barCount
     */
    static Double[] toDouble(Indicator<Num> ref, int index, int barCount) {
        int startIndex = Math.max(0, index - barCount + 1);
        return IntStream.range(startIndex, startIndex + barCount)
                .mapToObj(ref::getValue)
                .map(Num::doubleValue)
                .toArray(Double[]::new);
    }

    default Stream<T> stream() {
        return IntStream.range(getBarSeries().getBeginIndex(), getBarSeries().getEndIndex() + 1)
                .mapToObj(this::getValue);
    }

}
