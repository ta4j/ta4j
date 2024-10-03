/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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

import org.ta4j.core.num.Num;

public interface BarBuilder {
    BarBuilder timePeriod(Duration timePeriod);

    BarBuilder endTime(ZonedDateTime endTime);

    BarBuilder openPrice(Num openPrice);

    BarBuilder openPrice(Number openPrice);

    BarBuilder openPrice(String openPrice);

    BarBuilder highPrice(Number highPrice);

    BarBuilder highPrice(String highPrice);

    BarBuilder highPrice(Num highPrice);

    BarBuilder lowPrice(Num lowPrice);

    BarBuilder lowPrice(Number lowPrice);

    BarBuilder lowPrice(String lowPrice);

    BarBuilder closePrice(Num closePrice);

    BarBuilder closePrice(Number closePrice);

    BarBuilder closePrice(String closePrice);

    BarBuilder volume(Num volume);

    BarBuilder volume(Number volume);

    BarBuilder volume(String volume);

    BarBuilder amount(Num amount);

    BarBuilder amount(Number amount);

    BarBuilder amount(String amount);

    BarBuilder trades(long trades);

    BarBuilder trades(String trades);

    BarBuilder bindTo(BarSeries baseBarSeries);

    Bar build();

    void add();
}
