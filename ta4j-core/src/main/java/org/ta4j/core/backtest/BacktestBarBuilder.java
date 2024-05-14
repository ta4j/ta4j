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
package org.ta4j.core.backtest;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * A builder to build a new {@link BacktestBar}.
 */
public class BacktestBarBuilder implements BarBuilder {

    private Duration timePeriod;
    private ZonedDateTime endTime;
    private Num openPrice;
    private Num highPrice;
    private Num lowPrice;
    private Num closePrice;
    private Num volume;
    private Num amount;
    private long trades;
    private final NumFactory numFactory;
    private final BarSeries barSeries;

    /** Constructor to build a {@code BacktestBar}. */
    public BacktestBarBuilder(final BarSeries barSeries) {
        this.numFactory = barSeries.numFactory();
        this.barSeries = barSeries;
    }

    public BacktestBarBuilder trades(final String trades) {
        trades(Long.parseLong(trades));
        return this;
    }

    /**
     * @param openPrice the open price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarBuilder openPrice(final Number openPrice) {
        openPrice(this.numFactory.numOf(openPrice));
        return this;
    }

    /**
     * @param openPrice the open price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarBuilder openPrice(final String openPrice) {
        openPrice(this.numFactory.numOf(openPrice));
        return this;
    }

    /**
     * @param highPrice the highest price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarBuilder highPrice(final Number highPrice) {
        highPrice(this.numFactory.numOf(highPrice));
        return this;
    }

    /**
     * @param highPrice the highest price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarBuilder highPrice(final String highPrice) {
        highPrice(this.numFactory.numOf(highPrice));
        return this;
    }

    /**
     * @param lowPrice the lowest price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarBuilder lowPrice(final Number lowPrice) {
        lowPrice(this.numFactory.numOf(lowPrice));
        return this;
    }

    /**
     * @param lowPrice the lowest price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarBuilder lowPrice(final String lowPrice) {
        lowPrice(this.numFactory.numOf(lowPrice));
        return this;
    }

    /**
     * @param closePrice the close price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarBuilder closePrice(final Number closePrice) {
        closePrice(this.numFactory.numOf(closePrice));
        return this;
    }

    /**
     * @param closePrice the close price of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarBuilder closePrice(final String closePrice) {
        closePrice(this.numFactory.numOf(closePrice));
        return this;
    }

    /**
     * @param volume the total traded volume of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarBuilder volume(final Number volume) {
        volume(this.numFactory.numOf(volume));
        return this;
    }

    /**
     * @param volume the total traded volume of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarBuilder volume(final String volume) {
        volume(this.numFactory.numOf(volume));
        return this;
    }

    /**
     * @param amount the total traded amount of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarBuilder amount(final Number amount) {
        amount(this.numFactory.numOf(amount));
        return this;
    }

    /**
     * @param amount the total traded amount of the bar period
     *
     * @return {@code this}
     */
    public BacktestBarBuilder amount(final String amount) {
        amount(this.numFactory.numOf(amount));
        return this;
    }
    
    /**
     * @param timePeriod the time period
     * @return {@code this}
     */
    public BacktestBarBuilder timePeriod(final Duration timePeriod) {
        this.timePeriod = timePeriod;
        return this;
    }

    /**
     * @param endTime the end time of the bar period
     * @return {@code this}
     */
    public BacktestBarBuilder endTime(final ZonedDateTime endTime) {
        this.endTime = endTime;
        return this;
    }

    /**
     * @param openPrice the open price of the bar period
     * @return {@code this}
     */
    public BacktestBarBuilder openPrice(final Num openPrice) {
        this.openPrice = openPrice;
        return this;
    }

    /**
     * @param highPrice the highest price of the bar period
     * @return {@code this}
     */
    public BacktestBarBuilder highPrice(final Num highPrice) {
        this.highPrice = highPrice;
        return this;
    }

    /**
     * @param lowPrice the lowest price of the bar period
     * @return {@code this}
     */
    public BacktestBarBuilder lowPrice(final Num lowPrice) {
        this.lowPrice = lowPrice;
        return this;
    }

    /**
     * @param closePrice the close price of the bar period
     * @return {@code this}
     */
    public BacktestBarBuilder closePrice(final Num closePrice) {
        this.closePrice = closePrice;
        return this;
    }

    /**
     * @param volume the total traded volume of the bar period
     * @return {@code this}
     */
    public BacktestBarBuilder volume(final Num volume) {
        this.volume = volume;
        return this;
    }

    /**
     * @param amount the total traded amount of the bar period
     * @return {@code this}
     */
    public BacktestBarBuilder amount(final Num amount) {
        this.amount = amount;
        return this;
    }

    /**
     * @param trades the number of trades of the bar period
     * @return {@code this}
     */
    public BacktestBarBuilder trades(final long trades) {
        this.trades = trades;
        return this;
    }

    public BacktestBar build() {
        return new BacktestBar(
            this.timePeriod,
            this.endTime,
            this.openPrice,
            this.highPrice,
            this.lowPrice,
            this.closePrice,
            this.volume,
            this.amount,
            this.trades
        );
    }

    public void add() {
      this.barSeries.addBar(build());
    }
}
