package org.ta4j.core;


import org.ta4j.core.num.Num;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * End bar of a time period.
 * </p>
 * Bar object is aggregated open/high/low/close/volume/etc. data over a time period.
 */
public interface Bar extends Serializable {
    /**
     * @return the open price of the period
     */
    Num getOpenPrice();

    /**
     * @return the low price of the period
     */
    Num getLowPrice();

    /**
     * @return the high price of the period
     */
    Num getHighPrice();

    /**
     * @return the close price of the period
     */
    Num getClosePrice();

    /**
     * @return the whole tradeNum volume in the period
     */
    Num getVolume();

    /**
     * @return the number of trades in the period
     */
    int getTrades();

    /**
     * @return the whole traded amount of the period
     */
    Num getAmount();

    /**
     * @return the time period of the bar
     */
    Duration getTimePeriod();

    /**
     * @return the begin timestamp of the bar period
     */
    ZonedDateTime getBeginTime();

    /**
     * @return the end timestamp of the bar period
     */
    ZonedDateTime getEndTime();

    /**
     * @param timestamp a timestamp
     * @return true if the provided timestamp is between the begin time and the end time of the current period, false otherwise
     */
    default boolean inPeriod(ZonedDateTime timestamp) {
        return timestamp != null
                && !timestamp.isBefore(getBeginTime())
                && timestamp.isBefore(getEndTime());
    }

    /**
     * @return a human-friendly string of the end timestamp
     */
    default String getDateName() {
        return getEndTime().format(DateTimeFormatter.ISO_DATE_TIME);
    }

    /**
     * @return a even more human-friendly string of the end timestamp
     */
    default String getSimpleDateName() {
        return getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * @return true if this is a bearish bar, false otherwise
     */
    default boolean isBearish() {
        Num openPrice = getOpenPrice();
        Num closePrice = getClosePrice();
        return (openPrice != null) && (closePrice != null) && closePrice.isLessThan(openPrice);
    }

    /**
     * @return true if this is a bullish bar, false otherwise
     */
    default boolean isBullish() {
    	Num openPrice = getOpenPrice();
        Num closePrice = getClosePrice();
        return (openPrice != null) && (closePrice != null) && openPrice.isLessThan(closePrice);
    }

    /**
     * Adds a trade at the end of bar period.
     * @param tradeVolume the traded volume
     * @param tradePrice the price
     * @deprecated use corresponding function of TimeSeries
     */
    @Deprecated
    default void addTrade(double tradeVolume, double tradePrice, Function<Number, Num> numFunction) {
        addTrade(numFunction.apply(tradeVolume),numFunction.apply(tradePrice));
    }

    /**
     * Adds a trade at the end of bar period.
     * @param tradeVolume the traded volume
     * @param tradePrice the price
     * @deprecated use corresponding function of TimeSeries
     */
    @Deprecated
    default void addTrade(String tradeVolume, String tradePrice, Function<Number, Num> numFunction) {
        addTrade(numFunction.apply(new BigDecimal(tradeVolume)), numFunction.apply(new BigDecimal(tradePrice)));
    }

    /**
     * Adds a trade at the end of bar period.
     * @param tradeVolume the traded volume
     * @param tradePrice the price
     */
    void addTrade(Num tradeVolume, Num tradePrice);


    default void addPrice(String price, Function<Number, Num> numFunction){
        addPrice(numFunction.apply(new BigDecimal(price)));
    }

    default void addPrice(Number price, Function<Number, Num> numFunction){
        addPrice(numFunction.apply(price));
    }

    void addPrice(Num price);
}
