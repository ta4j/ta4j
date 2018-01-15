/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Base implementation of a {@link Bar}.
 * <p></p>
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
    private Decimal openPrice = null;
    /** Close price of the period */
    private Decimal closePrice = null;
    /** Max price of the period */
    private Decimal maxPrice = null;
    /** Min price of the period */
    private Decimal minPrice = null;
    /** Traded amount during the period */
    private Decimal amount = Decimal.ZERO;
    /** Volume of the period */
    private Decimal volume = Decimal.ZERO;
    /** Trade count */
    private int trades = 0;

    /**
     * Constructor.
     * @param timePeriod the time period
     * @param endTime the end time of the bar period
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime) {
        checkTimeArguments(timePeriod, endTime);
        this.timePeriod = timePeriod;
        this.endTime = endTime;
        this.beginTime = endTime.minus(timePeriod);
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
    public BaseBar(ZonedDateTime endTime, double openPrice, double highPrice, double lowPrice, double closePrice, double volume) {
        this(endTime, Decimal.valueOf(openPrice),
                Decimal.valueOf(highPrice),
                Decimal.valueOf(lowPrice),
                Decimal.valueOf(closePrice),
                Decimal.valueOf(volume));
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
    public BaseBar(ZonedDateTime endTime, String openPrice, String highPrice, String lowPrice, String closePrice, String volume) {
        this(endTime, Decimal.valueOf(openPrice),
                Decimal.valueOf(highPrice),
                Decimal.valueOf(lowPrice),
                Decimal.valueOf(closePrice),
                Decimal.valueOf(volume));
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
    public BaseBar(ZonedDateTime endTime, Decimal openPrice, Decimal highPrice, Decimal lowPrice, Decimal closePrice, Decimal volume) {
        this(Duration.ofDays(1), endTime, openPrice, highPrice, lowPrice, closePrice, volume);
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
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, Decimal openPrice, Decimal highPrice, Decimal lowPrice, Decimal closePrice, Decimal volume) {
        this(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, Decimal.ZERO);
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
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, Decimal openPrice, Decimal highPrice, Decimal lowPrice, Decimal closePrice, Decimal volume, Decimal amount) {
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
    public Decimal getOpenPrice() {
        return openPrice;
    }

    /**
     * @return the min price of the period
     */
    public Decimal getMinPrice() {
        return minPrice;
    }

    /**
     * @return the max price of the period
     */
    public Decimal getMaxPrice() {
        return maxPrice;
    }

    /**
     * @return the close price of the period
     */
    public Decimal getClosePrice() {
        return closePrice;
    }

    /**
     * @return the whole traded volume in the period
     */
    public Decimal getVolume() {
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
    public Decimal getAmount() {
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
    public void addTrade(Decimal tradeVolume, Decimal tradePrice) {
        if (openPrice == null) {
            openPrice = tradePrice;
        }
        closePrice = tradePrice;

        if (maxPrice == null) {
            maxPrice = tradePrice;
        } else {
            maxPrice = maxPrice.isLessThan(tradePrice) ? tradePrice : maxPrice;
        }
        if (minPrice == null) {
            minPrice = tradePrice;
        } else {
            minPrice = minPrice.isGreaterThan(tradePrice) ? tradePrice : minPrice;
        }
        volume = volume.plus(tradeVolume);
        amount = amount.plus(tradeVolume.multipliedBy(tradePrice));
        trades++;
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
    private void checkTimeArguments(Duration timePeriod, ZonedDateTime endTime) {
        if (timePeriod == null) {
            throw new IllegalArgumentException("Time period cannot be null");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("End time cannot be null");
        }
    }
}
