/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization 
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core;

import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Function;

/**
 * Base implementation of a {@link Bar}.
 * </p>
 */
public class BaseBar implements Bar {

	private static final long serialVersionUID = 8038383777467488147L;
	/** Time period (e.g. 1 day, 15 min, etc.) of the bar */
    private Duration timePeriod;
    /** End time of the bar */
    private ZonedDateTime endTime;
    /** Begin time of the bar */
    private ZonedDateTime beginTime;
    /** Open price of the period */
    private Num openPrice = null;
    /** Close price of the period */
    private Num closePrice = null;
    /** Max price of the period */
    private Num maxPrice = null;
    /** Min price of the period */
    private Num minPrice = null;
    /** Traded amount during the period */
    private Num amount;
    /** Volume of the period */
    private Num volume;
    /** Trade count */
    private int trades = 0;


    /**
     * Constructor.
     * @param timePeriod the time period
     * @param endTime the end time of the bar period
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, Function<Number, Num> numFunction) {
        checkTimeArguments(timePeriod, endTime);
        this.timePeriod = timePeriod;
        this.endTime = endTime;
        this.beginTime = endTime.minus(timePeriod);
        this.volume = numFunction.apply(0);
        this.amount = numFunction.apply(0);
    }

    /**
     * Constructor.
     * @param endTime the end time of the bar period
     * @param openPrice the open price of the bar period
     * @param highPrice the highest price of the bar period
     * @param lowPrice the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume the volume of the bar period
     */
    public BaseBar(ZonedDateTime endTime, double openPrice, double highPrice, double lowPrice, double closePrice, double volume, Function<Number, Num> numFunction) {
        this(endTime, numFunction.apply(openPrice),
                numFunction.apply(highPrice),
                numFunction.apply(lowPrice),
                numFunction.apply(closePrice),
                numFunction.apply(volume),numFunction.apply(0));
    }

    /**
     * Constructor.
     * @param endTime the end time of the bar period
     * @param openPrice the open price of the bar period
     * @param highPrice the highest price of the bar period
     * @param lowPrice the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume the volume of the bar period
     */
    public BaseBar(ZonedDateTime endTime, String openPrice, String highPrice, String lowPrice, String closePrice, String volume, Function<Number, Num> numFunction) {
        this(endTime, numFunction.apply(new BigDecimal(openPrice)),
                numFunction.apply(new BigDecimal(highPrice)),
                numFunction.apply(new BigDecimal(lowPrice)),
                numFunction.apply(new BigDecimal(closePrice)),
                numFunction.apply(new BigDecimal(volume)),numFunction.apply(0));
    }

    /**
     * Constructor.
     * @param endTime the end time of the bar period
     * @param openPrice the open price of the bar period
     * @param highPrice the highest price of the bar period
     * @param lowPrice the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume the volume of the bar period
     */
    public BaseBar(ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume, Num amount) {
        this(Duration.ofDays(1), endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount);
    }


    /**
     * Constructor.
     * @param timePeriod the time period
     * @param endTime the end time of the bar period
     * @param openPrice the open price of the bar period
     * @param highPrice the highest price of the bar period
     * @param lowPrice the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume the volume of the bar period
     * @param amount the amount of the bar period
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume, Num amount) {
        checkTimeArguments(timePeriod, endTime);
        this.timePeriod = timePeriod;
        this.endTime = endTime;
        this.beginTime = endTime.minus(timePeriod);
        this.openPrice = openPrice;
        this.maxPrice = highPrice;
        this.minPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.amount = amount;
    }

    /**
     * @return the open price of the period
     */
    public Num getOpenPrice() {
        return openPrice;
    }

    /**
     * @return the min price of the period
     */
    public Num getMinPrice() {
        return minPrice;
    }

    /**
     * @return the max price of the period
     */
    public Num getMaxPrice() {
        return maxPrice;
    }

    /**
     * @return the close price of the period
     */
    public Num getClosePrice() {
        return closePrice;
    }

    /**
     * @return the whole traded volume in the period
     */
    public Num getVolume() {
        return volume;
    }

    /**
     * @return the number of trades in the period
     */
    public int getTrades() {
        return trades;
    }

    /**
     * @return the whole traded amount of the period
     */
    public Num getAmount() {
        return amount;
    }

    /**
     * @return the time period of the bar
     */
    public Duration getTimePeriod() {
        return timePeriod;
    }

    /**
     * @return the begin timestamp of the bar period
     */
    public ZonedDateTime getBeginTime() {
        return beginTime;
    }

    /**
     * @return the end timestamp of the bar period
     */
    public ZonedDateTime getEndTime() {
        return endTime;
    }

    /**
     * Adds a trade at the end of bar period.
     * @param tradeVolume the traded volume
     * @param tradePrice the price
     */
    public void addTrade(Num tradeVolume, Num tradePrice) {
        addPrice(tradePrice);

        volume = volume.plus(tradeVolume);
        amount = amount.plus(tradeVolume.multipliedBy(tradePrice));
        trades++;
    }

    @Override
    public void addPrice(Num price) {
        if (openPrice == null) {
            openPrice = price;
        }

        closePrice = price;
        if (maxPrice == null) {
            maxPrice = price;
        } else if(maxPrice.isLessThan(price)) {
            maxPrice = price;
        }
        if (minPrice == null) {
            minPrice = price;
        } else if(minPrice.isGreaterThan(price)){
            minPrice = price;
        }
    }

    @Override
    public String toString() {
        return String.format("{end time: %1s, close price: %2$f, open price: %3$f, min price: %4$f, max price: %5$f, volume: %6$f}",
                endTime.withZoneSameInstant(ZoneId.systemDefault()), closePrice.doubleValue(), openPrice.doubleValue(), minPrice.doubleValue(), maxPrice.doubleValue(), volume.doubleValue());
    }

    /**
     * @param timePeriod the time period
     * @param endTime the end time of the bar
     * @throws IllegalArgumentException if one of the arguments is null
     */
    private static void checkTimeArguments(Duration timePeriod, ZonedDateTime endTime) {
        if (timePeriod == null) {
            throw new IllegalArgumentException("Time period cannot be null");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("End time cannot be null");
        }
    }
}
