/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

import org.ta4j.core.num.Num;

/**
 * End bar of a time period.
 * 一个时间段的结束栏。
 *
 * Bar object is aggregated open/high/low/close/volume/etc. data over a time period.
 * * 柱形对象聚合开盘/高/低/收盘/成交量/等。 一段时间内的数据。
 */
public interface Bar extends Serializable {
    /**
     * @return the open price of the period
     * * @return 期间的开盘价
     */
    Num getOpenPrice();

    /**
     * @return the low price of the period
     *      * @return the low price of the period
     */
    Num getLowPrice();

    /**
     * @return the high price of the period
     * * @return 期间的最高价
     */
    Num getHighPrice();

    /**
     * @return the close price of the period
     * * @return 期间的收盘价
     */
    Num getClosePrice();

    /**
     * @return the whole tradeNum volume in the period
     * * @return 整个期间的 tradeNum 交易量
     */
    Num getVolume();

    /**
     * @return the number of trades in the period
     * * @return 周期内的交易数量
     */
    long getTrades();

    /**
     * @return the whole traded amount of the period
     * * @return 期间的全部交易量
     */
    Num getAmount();

    /**
     * @return the time period of the bar
     * * @return 柱的时间段
     */
    Duration getTimePeriod();

    /**
     * @return the begin timestamp of the bar period
     * * @return 柱周期的开始时间戳
     */
    ZonedDateTime getBeginTime();

    /**
     * @return the end timestamp of the bar period
     * * @return 柱周期的结束时间戳
     */
    ZonedDateTime getEndTime();

    /**
     * @param timestamp a timestamp
     *                  时间戳
     * @return true if the provided timestamp is between the begin time and the end  time of the current period, false otherwise
     *          * @return 如果提供的时间戳在当前周期的开始时间和结束时间之间，则返回 true，否则返回 false
     */
    default boolean inPeriod(ZonedDateTime timestamp) {
        return timestamp != null && !timestamp.isBefore(getBeginTime()) && timestamp.isBefore(getEndTime());
    }

    /**
     * @return a human-friendly string of the end timestamp
     * * @return 结束时间戳的人性化字符串
     */
    default String getDateName() {
        return getEndTime().format(DateTimeFormatter.ISO_DATE_TIME);
    }

    /**
     * @return a even more human-friendly string of the end timestamp
     * * @return 一个更加人性化的结束时间戳字符串
     */
    default String getSimpleDateName() {
        return getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * @return true if this is a bearish bar, false otherwise
     * * @return 如果这是一个看跌柱，则返回 true，否则返回 false
     */
    default boolean isBearish() {
        Num openPrice = getOpenPrice();
        Num closePrice = getClosePrice();
        return (openPrice != null) && (closePrice != null) && closePrice.isLessThan(openPrice);
    }

    /**
     * @return true if this is a bullish bar, false otherwise
     * * @return 如果这是一个看涨柱，则返回 true，否则返回 false
     */
    default boolean isBullish() {
        Num openPrice = getOpenPrice();
        Num closePrice = getClosePrice();
        return (openPrice != null) && (closePrice != null) && openPrice.isLessThan(closePrice);
    }

    /**
     * Adds a trade at the end of bar period.
     * * 在柱周期结束时添加交易。
     * 
     * @param tradeVolume the traded volume
     *                    成交量
     * @param tradePrice  the price
     *                    价格
     * @deprecated use corresponding function of {@link BarSeries}
     * * @deprecated 使用 {@link BarSeries} 的对应功能
     *
     */
    @Deprecated
    default void addTrade(double tradeVolume, double tradePrice, Function<Number, Num> numFunction) {
        addTrade(numFunction.apply(tradeVolume), numFunction.apply(tradePrice));
    }

    /**
     * Adds a trade at the end of bar period.
     * 在柱周期结束时添加交易。
     * 
     * @param tradeVolume the traded volume
     *                    成交量
     * @param tradePrice  the price
     *                    价格
     * @deprecated use corresponding function of {@link BarSeries}
     * 使用 {@link BarSeries} 的相应功能
     */
    @Deprecated
    default void addTrade(String tradeVolume, String tradePrice, Function<Number, Num> numFunction) {
        addTrade(numFunction.apply(new BigDecimal(tradeVolume)), numFunction.apply(new BigDecimal(tradePrice)));
    }

    /**
     * Adds a trade at the end of bar period.
     * 在柱周期结束时添加交易。
     * 
     * @param tradeVolume the traded volume
     *                    成交量
     * @param tradePrice  the price
     *                    价格
     */
    void addTrade(Num tradeVolume, Num tradePrice);

    default void addPrice(String price, Function<Number, Num> numFunction) {
        addPrice(numFunction.apply(new BigDecimal(price)));
    }

    default void addPrice(Number price, Function<Number, Num> numFunction) {
        addPrice(numFunction.apply(price));
    }

    void addPrice(Num price);
}
