/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.time.Duration;
import java.time.Instant;

import org.ta4j.core.num.Num;

public interface BarBuilder {

    /**
     * @param timePeriod the time period (optional if {@link #beginTime(Instant)}
     *                   and {@link #endTime(Instant)} are given)
     * @return {@code this}
     */
    BarBuilder timePeriod(Duration timePeriod);

    /**
     * @param beginTime the begin time of the bar period (optional if
     *                  {@link #endTime(Instant)} is given)
     * @return {@code this}
     */
    BarBuilder beginTime(Instant beginTime);

    /**
     * @param endTime the end time of the bar period (optional if
     *                {@link #beginTime(Instant)} is given)
     * @return {@code this}
     */
    BarBuilder endTime(Instant endTime);

    /**
     * @param openPrice the open price of the bar period
     * @return {@code this}
     */
    BarBuilder openPrice(Num openPrice);

    /**
     * @param openPrice the open price of the bar period
     * @return {@code this}
     */
    BarBuilder openPrice(Number openPrice);

    /**
     * @param openPrice the open price of the bar period
     * @return {@code this}
     */
    BarBuilder openPrice(String openPrice);

    /**
     * @param highPrice the highest price of the bar period
     * @return {@code this}
     */
    BarBuilder highPrice(Number highPrice);

    /**
     * @param highPrice the highest price of the bar period
     * @return {@code this}
     */
    BarBuilder highPrice(String highPrice);

    /**
     * @param highPrice the highest price of the bar period
     * @return {@code this}
     */
    BarBuilder highPrice(Num highPrice);

    /**
     * @param lowPrice the lowest price of the bar period
     * @return {@code this}
     */
    BarBuilder lowPrice(Num lowPrice);

    /**
     * @param lowPrice the lowest price of the bar period
     * @return {@code this}
     */
    BarBuilder lowPrice(Number lowPrice);

    /**
     * @param lowPrice the lowest price of the bar period
     * @return {@code this}
     */
    BarBuilder lowPrice(String lowPrice);

    /**
     * @param closePrice the close price of the bar period
     * @return {@code this}
     */
    BarBuilder closePrice(Num closePrice);

    /**
     * @param closePrice the close price of the bar period
     * @return {@code this}
     */
    BarBuilder closePrice(Number closePrice);

    /**
     * @param closePrice the close price of the bar period
     * @return {@code this}
     */
    BarBuilder closePrice(String closePrice);

    /**
     * @param volume the total traded volume of the bar period
     * @return {@code this}
     */
    BarBuilder volume(Num volume);

    /**
     * @param volume the total traded volume of the bar period
     * @return {@code this}
     */
    BarBuilder volume(Number volume);

    /**
     * @param volume the total traded volume of the bar period
     * @return {@code this}
     */
    BarBuilder volume(String volume);

    /**
     * @param amount the total traded amount of the bar period (if {@code null},
     *               then it is calculated by {@code closePrice * volume})
     * @return {@code this}
     */
    BarBuilder amount(Num amount);

    /**
     * @param amount the total traded amount of the bar period (if {@code null},
     *               then it is calculated by {@code closePrice * volume})
     * @return {@code this}
     */
    BarBuilder amount(Number amount);

    /**
     * @param amount the total traded amount of the bar period (if {@code null},
     *               then it is calculated by {@code closePrice * volume})
     * @return {@code this}
     */
    BarBuilder amount(String amount);

    /**
     * @param trades the number of trades of the bar period
     * @return {@code this}
     */
    BarBuilder trades(long trades);

    /**
     * @param trades the number of trades of the bar period
     * @return {@code this}
     */
    BarBuilder trades(String trades);

    /**
     * Updates the builder with a trade event and adds or updates bars as needed.
     *
     * @param time        the trade timestamp (UTC)
     * @param tradeVolume the traded volume
     * @param tradePrice  the traded price
     *
     * @since 0.22.2
     */
    default void addTrade(Instant time, Num tradeVolume, Num tradePrice) {
        throw new UnsupportedOperationException("Trade ingestion not supported by " + getClass().getSimpleName());
    }

    /**
     * Updates the builder with a trade event and adds or updates bars as needed.
     *
     * @param time        the trade timestamp (UTC)
     * @param tradeVolume the traded volume
     * @param tradePrice  the traded price
     * @param side        aggressor side (optional)
     * @param liquidity   liquidity classification (optional)
     *
     * @since 0.22.2
     */
    default void addTrade(Instant time, Num tradeVolume, Num tradePrice, RealtimeBar.Side side,
            RealtimeBar.Liquidity liquidity) {
        addTrade(time, tradeVolume, tradePrice);
    }

    /**
     * @param barSeries the series used for bar addition
     * @return {@code this}
     */
    BarBuilder bindTo(BarSeries barSeries);

    /**
     * @return bar created from obtained data
     */
    Bar build();

    /**
     * Builds bar with {@link #build()} and adds it to series
     */
    void add();
}
