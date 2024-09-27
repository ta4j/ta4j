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
import org.ta4j.core.num.NumFactory;

/**
 * A builder to build a new {@code BaseBar} with conversion from a
 * {@link Number} of type {@code T} to a {@link Num Num implementation}.
 */
public class BaseBarConvertibleBuilder extends BaseBarBuilder {

    private final NumFactory numFactory;

    public BaseBarConvertibleBuilder(final NumFactory numFactory) {
        this.numFactory = numFactory;
    }

    @Override
    public BaseBarConvertibleBuilder timePeriod(final Duration timePeriod) {
        super.timePeriod(timePeriod);
        return this;
    }

    @Override
    public BaseBarConvertibleBuilder endTime(final ZonedDateTime endTime) {
        super.endTime(endTime);
        return this;
    }

    @Override
    public BaseBarConvertibleBuilder trades(final long trades) {
        super.trades(trades);
        return this;
    }

    public BaseBarConvertibleBuilder trades(final String trades) {
        super.trades(Long.parseLong(trades));
        return this;
    }

    /**
     * @param openPrice the open price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder openPrice(final Number openPrice) {
        super.openPrice(numFactory.numOf(openPrice));
        return this;
    }

    /**
     * @param openPrice the open price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder openPrice(final String openPrice) {
        super.openPrice(numFactory.numOf(openPrice));
        return this;
    }

    /**
     * @param highPrice the highest price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder highPrice(final Number highPrice) {
        super.highPrice(numFactory.numOf(highPrice));
        return this;
    }

    /**
     * @param highPrice the highest price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder highPrice(final String highPrice) {
        super.highPrice(numFactory.numOf(highPrice));
        return this;
    }

    /**
     * @param lowPrice the lowest price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder lowPrice(final Number lowPrice) {
        super.lowPrice(numFactory.numOf(lowPrice));
        return this;
    }

    /**
     * @param lowPrice the lowest price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder lowPrice(final String lowPrice) {
        super.lowPrice(numFactory.numOf(lowPrice));
        return this;
    }

    /**
     * @param closePrice the close price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder closePrice(final Number closePrice) {
        super.closePrice(numFactory.numOf(closePrice));
        return this;
    }

    /**
     * @param closePrice the close price of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder closePrice(final String closePrice) {
        super.closePrice(numFactory.numOf(closePrice));
        return this;
    }

    /**
     * @param volume the total traded volume of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder volume(final Number volume) {
        super.volume(numFactory.numOf(volume));
        return this;
    }

    /**
     * @param volume the total traded volume of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder volume(final String volume) {
        super.volume(numFactory.numOf(volume));
        return this;
    }

    /**
     * @param amount the total traded amount of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder amount(final Number amount) {
        super.amount(numFactory.numOf(amount));
        return this;
    }

    /**
     * @param amount the total traded amount of the bar period
     *
     * @return {@code this}
     */
    public BaseBarConvertibleBuilder amount(final String amount) {
        super.amount(numFactory.numOf(amount));
        return this;
    }

    @Override
    public BaseBarConvertibleBuilder bindTo(final BarSeries baseBarSeries) {
        super.bindTo(baseBarSeries);
        return this;
    }
}
