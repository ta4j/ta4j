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

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.function.Function;

import org.ta4j.core.num.Num;

/**
 * A builder to build a new {@code BaseBar} with conversion from a
 * {@link Number} of type {@code T} to a {@link Num Num implementation}.
 *
 * @param <T> a {@link Number} of type {@code T}
 */
public class BaseBarConvertibleBuilder<T> extends BaseBarBuilder {

    /**
     * The Num function to convert a {@link Number} to a {@link Num Num
     * implementation}.
     */
    private final Function<T, Num> conversionFunction;

    /**
     * Constructor to build a {@code BaseBar}.
     *
     * @param conversionFunction the Num function to convert a {@link Number} to a
     *                           {@link Num Num implementation}
     */
    public BaseBarConvertibleBuilder(Function<T, Num> conversionFunction) {
        this.conversionFunction = conversionFunction;
    }

    @Override
    public BaseBarConvertibleBuilder<T> timePeriod(Duration timePeriod) {
        super.timePeriod(timePeriod);
        return this;
    }

    @Override
    public BaseBarConvertibleBuilder<T> endTime(ZonedDateTime endTime) {
        super.endTime(endTime);
        return this;
    }

    @Override
    public BaseBarConvertibleBuilder<T> trades(long trades) {
        super.trades(trades);
        return this;
    }

    /**
     * @param openPrice the open price of the bar period
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder<T> openPrice(T openPrice) {
        super.openPrice(conversionFunction.apply(openPrice));
        return this;
    }

    /**
     * @param highPrice the highest price of the bar period
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder<T> highPrice(T highPrice) {
        super.highPrice(conversionFunction.apply(highPrice));
        return this;
    }

    /**
     * @param lowPrice the lowest price of the bar period
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder<T> lowPrice(T lowPrice) {
        super.lowPrice(conversionFunction.apply(lowPrice));
        return this;
    }

    /**
     * @param closePrice the close price of the bar period
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder<T> closePrice(T closePrice) {
        super.closePrice(conversionFunction.apply(closePrice));
        return this;
    }

    /**
     * @param volume the total traded volume of the bar period
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder<T> volume(T volume) {
        super.volume(conversionFunction.apply(volume));
        return this;
    }

    /**
     * @param amount the total traded amount of the bar period
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder<T> amount(T amount) {
        super.amount(conversionFunction.apply(amount));
        return this;
    }
}
