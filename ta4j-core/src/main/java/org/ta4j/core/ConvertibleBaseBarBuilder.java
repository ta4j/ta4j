/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2019 Ta4j Organization & respective
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

import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.function.Function;

public class ConvertibleBaseBarBuilder<T> extends BaseBarBuilder {

    private final Function<T, Num> conversionFunction;

    public ConvertibleBaseBarBuilder(Function<T, Num> conversionFunction) {
        this.conversionFunction = conversionFunction;
    }

    @Override
    public ConvertibleBaseBarBuilder<T> timePeriod(Duration timePeriod) {
        super.timePeriod(timePeriod);
        return this;
    }

    @Override
    public ConvertibleBaseBarBuilder<T> endTime(ZonedDateTime endTime) {
        super.endTime(endTime);
        return this;
    }

    @Override
    public ConvertibleBaseBarBuilder<T> trades(int trades) {
        super.trades(trades);
        return this;
    }

    public ConvertibleBaseBarBuilder<T> openPrice(T openPrice) {
        super.openPrice(conversionFunction.apply(openPrice));
        return this;
    }

    public ConvertibleBaseBarBuilder<T> highPrice(T highPrice) {
        super.highPrice(conversionFunction.apply(highPrice));
        return this;
    }

    public ConvertibleBaseBarBuilder<T> lowPrice(T lowPrice) {
        super.lowPrice(conversionFunction.apply(lowPrice));
        return this;
    }

    public ConvertibleBaseBarBuilder<T> closePrice(T closePrice) {
        super.closePrice(conversionFunction.apply(closePrice));
        return this;
    }

    public ConvertibleBaseBarBuilder<T> amount(T amount) {
        super.amount(conversionFunction.apply(amount));
        return this;
    }

    public ConvertibleBaseBarBuilder<T> volume(T volume) {
        super.volume(conversionFunction.apply(volume));
        return this;
    }
}
