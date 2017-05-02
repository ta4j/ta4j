/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j;


import java.io.Serializable;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Period;

/**
 * End tick of a time period.
 * <p>
 */
public class Tick implements Serializable {

	private static final long serialVersionUID = 8038383777467488147L;
	/** Time period (e.g. 1 day, 15 min, etc.) of the tick */
    private Period timePeriod;
    /** End time of the tick */
    private DateTime endTime;
    /** Begin time of the tick */
    private DateTime beginTime;
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
     * @param endTime the end time of the tick period
     */
    public Tick(Period timePeriod, DateTime endTime) {
        checkTimeArguments(timePeriod, endTime);
        this.timePeriod = timePeriod;
        this.endTime = endTime;
        this.beginTime = endTime.minus(timePeriod);
    }

    /**
     * Constructor.
     * @param endTime the end time of the tick period
     * @param openPrice the open price of the tick period
     * @param highPrice the highest price of the tick period
     * @param lowPrice the lowest price of the tick period
     * @param closePrice the close price of the tick period
     * @param volume the volume of the tick period
     */
    public Tick(DateTime endTime, double openPrice, double highPrice, double lowPrice, double closePrice, double volume) {
        this(endTime, Decimal.valueOf(openPrice),
                Decimal.valueOf(highPrice),
                Decimal.valueOf(lowPrice),
                Decimal.valueOf(closePrice),
                Decimal.valueOf(volume));
    }

    /**
     * Constructor.
     * @param endTime the end time of the tick period
     * @param openPrice the open price of the tick period
     * @param highPrice the highest price of the tick period
     * @param lowPrice the lowest price of the tick period
     * @param closePrice the close price of the tick period
     * @param volume the volume of the tick period
     */
    public Tick(DateTime endTime, String openPrice, String highPrice, String lowPrice, String closePrice, String volume) {
        this(endTime, Decimal.valueOf(openPrice),
                Decimal.valueOf(highPrice),
                Decimal.valueOf(lowPrice),
                Decimal.valueOf(closePrice),
                Decimal.valueOf(volume));
    }

    /**
     * Constructor.
     * @param endTime the end time of the tick period
     * @param openPrice the open price of the tick period
     * @param highPrice the highest price of the tick period
     * @param lowPrice the lowest price of the tick period
     * @param closePrice the close price of the tick period
     * @param volume the volume of the tick period
     */
    public Tick(DateTime endTime, Decimal openPrice, Decimal highPrice, Decimal lowPrice, Decimal closePrice, Decimal volume) {
        this(Days.days(1).toPeriod(), endTime, openPrice, highPrice, lowPrice, closePrice, volume);
    }

    /**
     * Constructor.
     * @param timePeriod the time period
     * @param endTime the end time of the tick period
     * @param openPrice the open price of the tick period
     * @param highPrice the highest price of the tick period
     * @param lowPrice the lowest price of the tick period
     * @param closePrice the close price of the tick period
     * @param volume the volume of the tick period
     */
    public Tick(Period timePeriod, DateTime endTime, Decimal openPrice, Decimal highPrice, Decimal lowPrice, Decimal closePrice, Decimal volume) {
        checkTimeArguments(timePeriod, endTime);
        this.timePeriod = timePeriod;
        this.endTime = endTime;
        this.beginTime = endTime.minus(timePeriod);
        this.openPrice = openPrice;
        this.maxPrice = highPrice;
        this.minPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }

    /**
     * @return the close price of the period
     */
    public Decimal getClosePrice() {
        return closePrice;
    }

    /**
     * @return the open price of the period
     */
    public Decimal getOpenPrice() {
        return openPrice;
    }

    /**
     * @return the number of trades in the period
     */
    public int getTrades() {
        return trades;
    }

    /**
     * @return the max price of the period
     */
    public Decimal getMaxPrice() {
        return maxPrice;
    }

    /**
     * @return the whole traded amount of the period
     */
    public Decimal getAmount() {
        return amount;
    }

    /**
     * @return the whole traded volume in the period
     */
    public Decimal getVolume() {
        return volume;
    }

    /**
     * Adds a trade at the end of tick period.
     * @param tradeAmount the tradable amount
     * @param tradePrice the price
     */
    public void addTrade(double tradeAmount, double tradePrice) {
        addTrade(Decimal.valueOf(tradeAmount), Decimal.valueOf(tradePrice));
    }

    /**
     * Adds a trade at the end of tick period.
     * @param tradeAmount the tradable amount
     * @param tradePrice the price
     */
    public void addTrade(String tradeAmount, String tradePrice) {
        addTrade(Decimal.valueOf(tradeAmount), Decimal.valueOf(tradePrice));
    }

    /**
     * Adds a trade at the end of tick period.
     * @param tradeAmount the tradable amount
     * @param tradePrice the price
     */
    public void addTrade(Decimal tradeAmount, Decimal tradePrice) {
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
        amount = amount.plus(tradeAmount);
        volume = volume.plus(tradeAmount.multipliedBy(tradePrice));
        trades++;
    }

    /**
     * @return the min price of the period
     */
    public Decimal getMinPrice() {
        return minPrice;
    }

    /**
     * @return the time period of the tick
     */
    public Period getTimePeriod() {
        return timePeriod;
    }

    /**
     * @return the begin timestamp of the tick period
     */
    public DateTime getBeginTime() {
        return beginTime;
    }

    /**
     * @return the end timestamp of the tick period
     */
    public DateTime getEndTime() {
        return endTime;
    }

    @Override
    public String toString() {
        return String.format("[time: %1$td/%1$tm/%1$tY %1$tH:%1$tM:%1$tS, close price: %2$f]",
                endTime.toGregorianCalendar(), closePrice.toDouble());
    }

    /**
     * @param timestamp a timestamp
     * @return true if the provided timestamp is between the begin time and the end time of the current period, false otherwise
     */
    public boolean inPeriod(DateTime timestamp) {
        return timestamp != null
                && !timestamp.isBefore(beginTime)
                && timestamp.isBefore(endTime);
    }

    /**
     * @return true if this is a bearish tick, false otherwise
     */
    public boolean isBearish() {
        return (openPrice != null) && (closePrice != null) && closePrice.isLessThan(openPrice);
    }

    /**
     * @return true if this is a bullish tick, false otherwise
     */
    public boolean isBullish() {
        return (openPrice != null) && (closePrice != null) && openPrice.isLessThan(closePrice);
    }
    
    /**
     * @return a human-friendly string of the end timestamp
     */
    public String getDateName() {
        return endTime.toString("hh:mm dd/MM/yyyy");
    }

    /**
     * @return a even more human-friendly string of the end timestamp
     */
    public String getSimpleDateName() {
        return endTime.toString("dd/MM/yyyy");
    }

    /**
     * @param timePeriod the time period
     * @param endTime the end time of the tick
     * @throws IllegalArgumentException if one of the arguments is null
     */
    private void checkTimeArguments(Period timePeriod, DateTime endTime) {
        if (timePeriod == null) {
            throw new IllegalArgumentException("Time period cannot be null");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("End time cannot be null");
        }
    }
}
