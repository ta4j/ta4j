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
package org.ta4j.core.bars;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * A builder to build a new {@link BaseBar}.
 */
public class BaseBarBuilder implements BarBuilder {

    private final NumFactory numFactory;
    private Duration timePeriod;
    private Instant endTime;
    private Num openPrice;
    private Num highPrice;
    private Num lowPrice;
    private Num closePrice;
    private Num volume;
    private Num amount;
    private long trades;
    private BarSeries baseBarSeries;

    public BaseBarBuilder() {
        this(DoubleNumFactory.getInstance());
    }

    public BaseBarBuilder(final NumFactory numFactory) {
        this.numFactory = numFactory;
    }

    @Override
    public BaseBarBuilder timePeriod(final Duration timePeriod) {
        this.timePeriod = timePeriod;
        return this;
    }

    @Override
    public BaseBarBuilder endTime(Instant endTime) {
        this.endTime = endTime;
        return this;
    }

    @Override
    public BaseBarBuilder openPrice(final Num openPrice) {
        this.openPrice = openPrice;
        return this;
    }

    @Override
    public BarBuilder openPrice(final Number openPrice) {
        openPrice(this.numFactory.numOf(openPrice));
        return this;
    }

    @Override
    public BarBuilder openPrice(final String openPrice) {
        openPrice(this.numFactory.numOf(openPrice));
        return this;
    }

    @Override
    public BarBuilder highPrice(final Number highPrice) {
        highPrice(this.numFactory.numOf(highPrice));
        return this;
    }

    @Override
    public BarBuilder highPrice(final String highPrice) {
        highPrice(this.numFactory.numOf(highPrice));
        return this;
    }

    @Override
    public BaseBarBuilder highPrice(final Num highPrice) {
        this.highPrice = highPrice;
        return this;
    }

    @Override
    public BaseBarBuilder lowPrice(final Num lowPrice) {
        this.lowPrice = lowPrice;
        return this;
    }

    @Override
    public BarBuilder lowPrice(final Number lowPrice) {
        lowPrice(this.numFactory.numOf(lowPrice));
        return this;
    }

    @Override
    public BarBuilder lowPrice(final String lowPrice) {
        lowPrice(this.numFactory.numOf(lowPrice));
        return this;
    }

    @Override
    public BaseBarBuilder closePrice(final Num closePrice) {
        this.closePrice = closePrice;
        return this;
    }

    @Override
    public BarBuilder closePrice(final Number closePrice) {
        closePrice(this.numFactory.numOf(closePrice));
        return this;
    }

    @Override
    public BarBuilder closePrice(final String closePrice) {
        closePrice(this.numFactory.numOf(closePrice));
        return this;
    }

    @Override
    public BaseBarBuilder volume(final Num volume) {
        this.volume = volume;
        return this;
    }

    @Override
    public BarBuilder volume(final Number volume) {
        volume(this.numFactory.numOf(volume));
        return this;
    }

    @Override
    public BarBuilder volume(final String volume) {
        volume(this.numFactory.numOf(volume));
        return this;
    }

    @Override
    public BaseBarBuilder amount(final Num amount) {
        this.amount = amount;
        return this;
    }

    @Override
    public BarBuilder amount(final Number amount) {
        amount(this.numFactory.numOf(amount));
        return this;
    }

    @Override
    public BarBuilder amount(final String amount) {
        amount(this.numFactory.numOf(amount));
        return this;
    }

    @Override
    public BaseBarBuilder trades(final long trades) {
        this.trades = trades;
        return this;
    }

    @Override
    public BaseBarBuilder trades(final String trades) {
        trades(Long.parseLong(trades));
        return this;
    }

    @Override
    public BaseBarBuilder bindTo(final BarSeries barSeries) {
        this.baseBarSeries = Objects.requireNonNull(barSeries);
        return this;
    }

    @Override
    public BaseBar build() {
        return new BaseBar(this.timePeriod, this.endTime, this.openPrice, this.highPrice, this.lowPrice,
                this.closePrice, this.volume, this.amount, this.trades);
    }

    @Override
    public void add() {
        this.baseBarSeries.addBar(build());
    }
}
