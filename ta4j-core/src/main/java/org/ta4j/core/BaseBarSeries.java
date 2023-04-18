/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Base implementation of a {@link BarSeries}.
 * </p>
 */
public class BaseBarSeries extends BarSeriesAbstract<BaseBar> {

    private static final long serialVersionUID = -1878027009398790126L;

    public BaseBarSeries() {
        super();
    }

    public BaseBarSeries(String name) {
        super(name);
    }

    public BaseBarSeries(List<BaseBar> bars) {
        super(bars);
    }

    public BaseBarSeries(String name, List<BaseBar> bars) {
        super(name, bars);
    }

    public BaseBarSeries(String name, Num num) {
        super(name, num);
    }

    public BaseBarSeries(String name, List<BaseBar> bars, Num num) {
        super(name, bars, num);
    }

    public BaseBarSeries(String name, List<BaseBar> bars, int seriesBeginIndex, int seriesEndIndex, boolean constrained, Num num) {
        super(name, bars, seriesBeginIndex, seriesEndIndex, constrained, num);
    }

    /**
     * Returns a new BaseBarSeries that is a subset of this BaseBarSeries. The new
     * series holds a copy of all {@link Bar bars} between <tt>startIndex</tt>
     * (inclusive) and <tt>endIndex</tt> (exclusive) of this BaseBarSeries. The
     * indices of this BaseBarSeries and the new subset BaseBarSeries can be
     * different. I. e. index 0 of the new BaseBarSeries will be index
     * <tt>startIndex</tt> of this BaseBarSeries. If <tt>startIndex</tt> <
     * this.seriesBeginIndex the new BaseBarSeries will start with the first
     * available Bar of this BaseBarSeries. If <tt>endIndex</tt> >
     * this.seriesEndIndex+1 the new BaseBarSeries will end at the last available
     * Bar of this BaseBarSeries
     *
     * @param startIndex the startIndex (inclusive)
     * @param endIndex   the endIndex (exclusive)
     * @return a new BarSeries with Bars from startIndex to endIndex-1
     * @throws IllegalArgumentException if endIndex <= startIndex or startIndex < 0
     */
    @Override
    public BaseBarSeries getSubSeries(int startIndex, int endIndex) {
        if (startIndex < 0) {
            throw new IllegalArgumentException(String.format("the startIndex: %s must not be negative", startIndex));
        }
        if (startIndex >= endIndex) {
            throw new IllegalArgumentException(
                    String.format("the endIndex: %s must be greater than startIndex: %s", endIndex, startIndex));
        }
        if (!bars.isEmpty()) {
            int start = Math.max(startIndex - getRemovedBarsCount(), this.getBeginIndex());
            int end = Math.min(endIndex - getRemovedBarsCount(), this.getEndIndex() + 1);
            return new BaseBarSeries(getName(), cut(bars, start, end), num);
        }
        return new BaseBarSeries(name, num);

    }

    @Override
    public void addBar(Duration timePeriod, ZonedDateTime endTime) {
        this.addBar(new BaseBar(timePeriod, endTime, function()));
    }

    @Override
    public void addBar(ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume) {
        this.addBar(
                new BaseBar(Duration.ofDays(1), endTime, openPrice, highPrice, lowPrice, closePrice, volume, zero()));
    }

    @Override
    public void addBar(ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume,
            Num amount) {
        this.addBar(
                new BaseBar(Duration.ofDays(1), endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount));
    }

    @Override
    public void addBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice,
            Num closePrice, Num volume) {
        this.addBar(new BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, zero()));
    }

    @Override
    public void addBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice,
            Num closePrice, Num volume, Num amount) {
        this.addBar(new BaseBar(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount));
    }

    @Override
    public void addTrade(Number price, Number amount) {
        addTrade(numOf(price), numOf(amount));
    }

    @Override
    public void addTrade(String price, String amount) {
        addTrade(numOf(new BigDecimal(price)), numOf(new BigDecimal(amount)));
    }

}
