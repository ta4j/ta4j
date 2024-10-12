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
import java.time.Instant;

import org.ta4j.core.num.Num;

public interface BarBuilder {
    /**
     * @param timePeriod the time period
     * @return {@code this}
     */
    BarBuilder timePeriod(Duration timePeriod);

    /**
     * @param endTime the end time of the bar period
     * @return {@code this}
     */
    BarBuilder endTime(Instant endTime);

    /**
     * @param openPrice the open price of the bar period
     * @return {@code this}
     */
    BarBuilder openPrice(Num openPrice);

    /**
     * @param openPrice the open price of the bar period
     * @return {@code this}
     */
    BarBuilder openPrice(Number openPrice);

    /**
     * @param openPrice the open price of the bar period
     * @return {@code this}
     */
    BarBuilder openPrice(String openPrice);

    /**
     * @param highPrice the highest price of the bar period
     * @return {@code this}
     */
    BarBuilder highPrice(Number highPrice);

    /**
     * @param highPrice the highest price of the bar period
     * @return {@code this}
     */
    BarBuilder highPrice(String highPrice);

    /**
     * @param highPrice the highest price of the bar period
     * @return {@code this}
     */
    BarBuilder highPrice(Num highPrice);

    /**
     * @param lowPrice the lowest price of the bar period
     * @return {@code this}
     */
    BarBuilder lowPrice(Num lowPrice);

    /**
     * @param lowPrice the lowest price of the bar period
     * @return {@code this}
     */
    BarBuilder lowPrice(Number lowPrice);

    /**
     * @param lowPrice the lowest price of the bar period
     * @return {@code this}
     */
    BarBuilder lowPrice(String lowPrice);

    /**
     * @param closePrice the close price of the bar period
     * @return {@code this}
     */
    BarBuilder closePrice(Num closePrice);

    /**
     * @param closePrice the close price of the bar period
     * @return {@code this}
     */
    BarBuilder closePrice(Number closePrice);

    /**
     * @param closePrice the close price of the bar period
     * @return {@code this}
     */
    BarBuilder closePrice(String closePrice);

    /**
     * @param volume the total traded volume of the bar period
     * @return {@code this}
     */
    BarBuilder volume(Num volume);

    /**
     * @param volume the total traded volume of the bar period
     * @return {@code this}
     */
    BarBuilder volume(Number volume);

    /**
     * @param volume the total traded volume of the bar period
     * @return {@code this}
     */
    BarBuilder volume(String volume);

    /**
     * @param amount the total traded amount of the bar period
     * @return {@code this}
     */
    BarBuilder amount(Num amount);

    /**
     * @param amount the total traded amount of the bar period
     * @return {@code this}
     */
    BarBuilder amount(Number amount);

    /**
     * @param amount the total traded amount of the bar period
     * @return {@code this}
     */
    BarBuilder amount(String amount);

    /**
     * @param trades the number of trades of the bar period
     * @return {@code this}
     */
    BarBuilder trades(long trades);

    /**
     * @param trades the number of trades of the bar period
     * @return {@code this}
     */
    BarBuilder trades(String trades);

    /**
     * @param barSeries the series used for bar addition
     * @return {@code this}
     */
    BarBuilder bindTo(BarSeries barSeries);

    /**
     * @return bar created from obtained data
     */
    Bar build();

    /**
     * Builds bar with {@link #build()} and adds it to series
     */
    void add();
}
