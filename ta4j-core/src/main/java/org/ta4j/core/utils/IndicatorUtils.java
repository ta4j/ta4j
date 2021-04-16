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
package org.ta4j.core.utils;

import org.ta4j.core.Indicator;

import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class IndicatorUtils {

    /**
     * Creates a stream from the indicator values
     *
     * @param indicator indicator to be used
     * @param <T>       Indicator type
     * @return the stream of values
     */
    public static <T> Stream<T> streamOf(final Indicator<T> indicator) {
        return IntStream.range(0, indicator.getBarSeries()
                .getBarCount())
                .mapToObj(indicator::getValue);
    }

    /**
     * Creates a stream from the indicator values and applies a mapping function on each element.
     * Indicator context is passed together with the index, so other values could be used for mapping.
     * @param indicator indicator to be used
     * @param mapFunction function that is applied in mapping receives a tuple in argument; that contains the indicator
     *                   and integer index
     * @param <T> type of indicator value
     * @param <R> result type
     * @return the resulting type
     */
    public static <T, R> Stream<R> streamOf(final Indicator<T> indicator,
                                            final Function<IndicatorContext<T>, R> mapFunction) {
        return IntStream.range(0, indicator.getBarSeries()
                .getBarCount())
                .mapToObj((int i) -> mapFunction.apply(new IndicatorContext<>(indicator, i)));
    }

    public static final class IndicatorContext<T> {
        private final Indicator<T> indicator;
        private final int index;

        public IndicatorContext(Indicator<T> indicator, int index) {
            this.indicator = indicator;
            this.index = index;
        }

        public Indicator<T> getIndicator() {
            return indicator;
        }

        public int getIndex() {
            return index;
        }
    }
}
