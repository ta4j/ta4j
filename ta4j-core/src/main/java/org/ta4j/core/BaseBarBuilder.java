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

public class BaseBarBuilder {

    private Duration timePeriod;
    private ZonedDateTime endTime;
    private Num openPrice;
    private Num closePrice;
    private Num highPrice;
    private Num lowPrice;
    private Num amount;
    private Num volume;
    private int trades;

    BaseBarBuilder() {
    }

    public BaseBarBuilder timePeriod(Duration timePeriod) {
        this.timePeriod = timePeriod;
        return this;
    }

    public BaseBarBuilder endTime(ZonedDateTime endTime) {
        this.endTime = endTime;
        return this;
    }

    public BaseBarBuilder openPrice(Num openPrice) {
        this.openPrice = openPrice;
        return this;
    }

    public BaseBarBuilder closePrice(Num closePrice) {
        this.closePrice = closePrice;
        return this;
    }

    public BaseBarBuilder highPrice(Num highPrice) {
        this.highPrice = highPrice;
        return this;
    }

    public BaseBarBuilder lowPrice(Num lowPrice) {
        this.lowPrice = lowPrice;
        return this;
    }

    public BaseBarBuilder amount(Num amount) {
        this.amount = amount;
        return this;
    }

    public BaseBarBuilder volume(Num volume) {
        this.volume = volume;
        return this;
    }

    public BaseBarBuilder trades(int trades) {
        this.trades = trades;
        return this;
    }

    public BaseBar build() {
        return new BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
    }
}
