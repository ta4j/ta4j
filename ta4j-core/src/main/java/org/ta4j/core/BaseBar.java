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
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.PrecisionNum;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.function.Function;

/**
 * Base implementation of a {@link Bar}.
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
    private Num highPrice = null;
    /** Min price of the period */
    private Num lowPrice = null;
    /** Traded amount during the period */
    private Num amount;
    /** Volume of the period */
    private Num volume;
    /** Trade count */
    private int trades = 0;

    /**
     * Constructor.
     * 
     * @param timePeriod  the time period
     * @param endTime     the end time of the bar period
     * @param numFunction the numbers precision
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
     * 
     * @param timePeriod the time period
     * @param endTime    the end time of the bar period
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the volume of the bar period
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, double openPrice, double highPrice, double lowPrice,
            double closePrice, double volume) {
        this(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, 0.0);
    }

    /**
     * Constructor.
     * 
     * @param timePeriod the time period
     * @param endTime    the end time of the bar period
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the volume of the bar period
     * @param amount     the amount of the bar period
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, double openPrice, double highPrice, double lowPrice,
            double closePrice, double volume, double amount) {
        this(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, 0, DoubleNum::valueOf);
    }

    /**
     * Constructor.
     * 
     * @param timePeriod  the time period
     * @param endTime     the end time of the bar period
     * @param openPrice   the open price of the bar period
     * @param highPrice   the highest price of the bar period
     * @param lowPrice    the lowest price of the bar period
     * @param closePrice  the close price of the bar period
     * @param volume      the volume of the bar period
     * @param amount      the amount of the bar period
     * @param trades      the trades count of the bar period
     * @param numFunction the numbers precision
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, double openPrice, double highPrice, double lowPrice,
            double closePrice, double volume, double amount, int trades, Function<Number, Num> numFunction) {
        this(timePeriod, endTime, numFunction.apply(openPrice), numFunction.apply(highPrice),
                numFunction.apply(lowPrice), numFunction.apply(closePrice), numFunction.apply(volume),
                numFunction.apply(amount), trades);
    }

    /**
     * Constructor.
     * 
     * @param timePeriod the time period
     * @param endTime    the end time of the bar period
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the volume of the bar period
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, BigDecimal openPrice, BigDecimal highPrice,
            BigDecimal lowPrice, BigDecimal closePrice, BigDecimal volume) {
        this(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, BigDecimal.ZERO);
    }

    /**
     * Constructor.
     * 
     * @param timePeriod the time period
     * @param endTime    the end time of the bar period
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the volume of the bar period
     * @param amount     the amount of the bar period
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, BigDecimal openPrice, BigDecimal highPrice,
            BigDecimal lowPrice, BigDecimal closePrice, BigDecimal volume, BigDecimal amount) {
        this(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, 0, PrecisionNum::valueOf);
    }

    /**
     * Constructor.
     * 
     * @param timePeriod  the time period
     * @param endTime     the end time of the bar period
     * @param openPrice   the open price of the bar period
     * @param highPrice   the highest price of the bar period
     * @param lowPrice    the lowest price of the bar period
     * @param closePrice  the close price of the bar period
     * @param volume      the volume of the bar period
     * @param amount      the amount of the bar period
     * @param trades      the trades count of the bar period
     * @param numFunction the numbers precision
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, BigDecimal openPrice, BigDecimal highPrice,
            BigDecimal lowPrice, BigDecimal closePrice, BigDecimal volume, BigDecimal amount, int trades,
            Function<Number, Num> numFunction) {
        this(timePeriod, endTime, numFunction.apply(openPrice), numFunction.apply(highPrice),
                numFunction.apply(lowPrice), numFunction.apply(closePrice), numFunction.apply(volume),
                numFunction.apply(amount), trades);
    }

    /**
     * Constructor.
     * 
     * @param timePeriod the time period
     * @param endTime    the end time of the bar period
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the volume of the bar period
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, String openPrice, String highPrice, String lowPrice,
            String closePrice, String volume) {
        this(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, "0");
    }

    /**
     * Constructor.
     * 
     * @param timePeriod the time period
     * @param endTime    the end time of the bar period
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the volume of the bar period
     * @param amount     the amount of the bar period
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, String openPrice, String highPrice, String lowPrice,
            String closePrice, String volume, String amount) {
        this(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, "0",
                PrecisionNum::valueOf);
    }

    /**
     * Constructor.
     * 
     * @param timePeriod  the time period
     * @param endTime     the end time of the bar period
     * @param openPrice   the open price of the bar period
     * @param highPrice   the highest price of the bar period
     * @param lowPrice    the lowest price of the bar period
     * @param closePrice  the close price of the bar period
     * @param volume      the volume of the bar period
     * @param amount      the amount of the bar period
     * @param trades      the trades count of the bar period
     * @param numFunction the numbers precision
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, String openPrice, String highPrice, String lowPrice,
            String closePrice, String volume, String amount, String trades, Function<Number, Num> numFunction) {
        this(timePeriod, endTime, numFunction.apply(new BigDecimal(openPrice)),
                numFunction.apply(new BigDecimal(highPrice)), numFunction.apply(new BigDecimal(lowPrice)),
                numFunction.apply(new BigDecimal(closePrice)), numFunction.apply(new BigDecimal(volume)),
                numFunction.apply(new BigDecimal(amount)), Integer.valueOf(trades));
    }

    /**
     * Constructor.
     * 
     * @param timePeriod the time period
     * @param endTime    the end time of the bar period
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the volume of the bar period
     * @param amount     the amount of the bar period
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice,
            Num closePrice, Num volume, Num amount) {
        this(timePeriod, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, 0);
    }

    /**
     * Constructor.
     * 
     * @param timePeriod the time period
     * @param endTime    the end time of the bar period
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the volume of the bar period
     * @param amount     the amount of the bar period
     * @param trades     the trades count of the bar period
     */
    public BaseBar(Duration timePeriod, ZonedDateTime endTime, Num openPrice, Num highPrice, Num lowPrice,
            Num closePrice, Num volume, Num amount, int trades) {
        checkTimeArguments(timePeriod, endTime);
        this.timePeriod = timePeriod;
        this.endTime = endTime;
        this.beginTime = endTime.minus(timePeriod);
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.amount = amount;
        this.trades = trades;
    }

    /**
     * Returns BaseBarBuilder
     * 
     * @return builder of class BaseBarBuilder
     */
    public static BaseBarBuilder builder() {
        return new BaseBarBuilder();
    }

    /**
     * Returns BaseBarBuilder
     * 
     * @return builder of class BaseBarBuilder
     */
    public static <T> ConvertibleBaseBarBuilder<T> builder(Function<T, Num> conversionFunction, Class<T> clazz) {
        return new ConvertibleBaseBarBuilder<>(conversionFunction);
    }

    /**
     * @return the open price of the period
     */
    public Num getOpenPrice() {
        return openPrice;
    }

    /**
     * @return the low price of the period
     */
    public Num getLowPrice() {
        return lowPrice;
    }

    /**
     * @return the high price of the period
     */
    public Num getHighPrice() {
        return highPrice;
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
     * @return the whole traded amount (tradePrice x tradeVolume) of the period
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
     * 
     * @param tradeVolume the traded volume
     * @param tradePrice  the price
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
        if (highPrice == null || highPrice.isLessThan(price)) {
            highPrice = price;
        }
        if (lowPrice == null || lowPrice.isGreaterThan(price)) {
            lowPrice = price;
        }
    }

    @Override
    public String toString() {
        return String.format(
                "{end time: %1s, close price: %2$f, open price: %3$f, min price: %4$f, max price: %5$f, volume: %6$f}",
                endTime.withZoneSameInstant(ZoneId.systemDefault()), closePrice.doubleValue(), openPrice.doubleValue(),
                lowPrice.doubleValue(), highPrice.doubleValue(), volume.doubleValue());
    }

    /**
     * @param timePeriod the time period
     * @param endTime    the end time of the bar
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

    @Override
    public int hashCode() {
        return Objects.hash(beginTime, endTime, timePeriod, openPrice, highPrice, lowPrice, closePrice, volume, amount,
                trades);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof BaseBar))
            return false;
        final BaseBar other = (BaseBar) obj;
        return Objects.equals(beginTime, other.beginTime) && Objects.equals(endTime, other.endTime)
                && Objects.equals(timePeriod, other.timePeriod) && Objects.equals(openPrice, other.openPrice)
                && Objects.equals(highPrice, other.highPrice) && Objects.equals(lowPrice, other.lowPrice)
                && Objects.equals(closePrice, other.closePrice) && Objects.equals(volume, other.volume)
                && Objects.equals(amount, other.amount) && trades == other.trades;
    }
}
