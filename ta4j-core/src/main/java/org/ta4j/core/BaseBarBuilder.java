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

import org.ta4j.core.num.Num;

/**
 * A builder to build a new {@link BaseBar}.
 */
public class BaseBarBuilder {

    private Duration timePeriod;
    private ZonedDateTime endTime;
    private Num openPrice;
    private Num highPrice;
    private Num lowPrice;
    private Num closePrice;
    private Num volume;
    private Num amount;
    private long trades;

    /** Constructor to build a {@code BaseBar}. */
    BaseBarBuilder() {
    }

    /**
     * @param timePeriod the time period
     * @return {@code this}
     */
    public BaseBarBuilder timePeriod(Duration timePeriod) {
        this.timePeriod = timePeriod;
        return this;
    }

    /**
     * @param endTime the end time of the bar period
     * @return {@code this}
     */
    public BaseBarBuilder endTime(ZonedDateTime endTime) {
        this.endTime = endTime;
        return this;
    }

    /**
     * @param openPrice the open price of the bar period
     * @return {@code this}
     */
    public BaseBarBuilder openPrice(Num openPrice) {
        this.openPrice = openPrice;
        return this;
    }

    /**
     * @param highPrice the highest price of the bar period
     * @return {@code this}
     */
    public BaseBarBuilder highPrice(Num highPrice) {
        this.highPrice = highPrice;
        return this;
    }

    /**
     * @param lowPrice the lowest price of the bar period
     * @return {@code this}
     */
    public BaseBarBuilder lowPrice(Num lowPrice) {
        this.lowPrice = lowPrice;
        return this;
    }

    /**
     * @param closePrice the close price of the bar period
     * @return {@code this}
     */
    public BaseBarBuilder closePrice(Num closePrice) {
        this.closePrice = closePrice;
        return this;
    }

    /**
     * @param volume the total traded volume of the bar period
     * @return {@code this}
     */
    public BaseBarBuilder volume(Num volume) {
        this.volume = volume;
        return this;
    }

    /**
     * @param amount the total traded amount of the bar period
     * @return {@code this}
     */
    public BaseBarBuilder amount(Num amount) {
        this.amount = amount;
        return this;
    }

    /**
     * @param trades the number of trades of the bar period
     * @return {@code this}
     */
    public BaseBarBuilder trades(long trades) {
        this.trades = trades;
        return this;
    }

    public BaseBar build() {
        return new BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
    }
}
