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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

/**
 * Base implementation of a {@link Bar}.
 */
public class BacktestBar implements Bar {

    /** The time period (e.g. 1 day, 15 min, etc.) of the bar. */
    private final Duration timePeriod;

    /** The begin time of the bar period. */
    private final ZonedDateTime beginTime;

    /** The end time of the bar period. */
    private final ZonedDateTime endTime;

    /** The open price of the bar period. */
    private Num openPrice;

    /** The high price of the bar period. */
    private Num highPrice;

    /** The low price of the bar period. */
    private Num lowPrice;

    /** The close price of the bar period. */
    private Num closePrice;

    /** The total traded volume of the bar period. */
    private Num volume;

    /** The total traded amount of the bar period. */
    private Num amount;

    /** The number of trades of the bar period. */
    private long trades;

    /**
     * Constructor.
     *
     * @param timePeriod the time period
     * @param endTime    the end time of the bar period
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the total traded volume of the bar period
     * @param amount     the total traded amount of the bar period
     * @param trades     the number of trades of the bar period
     */
    BacktestBar(
        final Duration timePeriod, final ZonedDateTime endTime, final Num openPrice, final Num highPrice, final Num lowPrice, final Num closePrice,
            final Num volume, final Num amount, final long trades) {
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
     * @return the time period of the bar (must be the same for all bars within the
     *         same {@code BarSeries})
     */
    @Override
    public Duration timePeriod() {
        return this.timePeriod;
    }

    /**
     * @return the begin timestamp of the bar period (derived by {@link #endTime} -
     *         {@link #timePeriod})
     */
    @Override
    public ZonedDateTime beginTime() {
        return this.beginTime;
    }

    @Override
    public ZonedDateTime endTime() {
        return this.endTime;
    }

    @Override
    public Num openPrice() {
        return this.openPrice;
    }

    @Override
    public Num highPrice() {
        return this.highPrice;
    }

    @Override
    public Num lowPrice() {
        return this.lowPrice;
    }

    @Override
    public Num closePrice() {
        return this.closePrice;
    }

    @Override
    public Num volume() {
        return this.volume;
    }


    /**
     * @return the total traded amount (tradePrice x tradeVolume) of the bar period
     */
    public Num getAmount() {
        return this.amount;
    }

    /**
     * @return the number of trades of the bar period
     */
    public long getTrades() {
        return this.trades;
    }

    /**
     * Adds a trade and updates the close price at the end of the bar period.
     *
     * @param tradeVolume the traded volume
     * @param tradePrice  the actual price per asset
     */
    public void addTrade(final Num tradeVolume, final Num tradePrice) {
        addPrice(tradePrice);

      this.volume = this.volume.plus(tradeVolume);
      this.amount = this.amount.plus(tradeVolume.multipliedBy(tradePrice));
      this.trades++;
    }




    /**
     * Updates the close price at the end of the bar period. The open, high and low
     * prices are also updated as needed.
     *
     * @param price       the actual price per asset
     * @param numFunction the numbers precision
     */
    void addPrice(final String price, final Function<Number, Num> numFunction) {
        addPrice(numFunction.apply(new BigDecimal(price)));
    }

    /**
     * Updates the close price at the end of the bar period. The open, high and low
     * prices are also updated as needed.
     *
     * @param price       the actual price per asset
     * @param numFunction the numbers precision
     */
    void addPrice(final Number price, final Function<Number, Num> numFunction) {
        addPrice(numFunction.apply(price));
    }

    /**
     * Updates the close price at the end of the bar period. The open, high and low
     * prices are also updated as needed.
     *
     * @param price the actual price per asset
     */
    public void addPrice(final Num price) {
        if (this.openPrice == null) {
          this.openPrice = price;
        }
      this.closePrice = price;
        if (this.highPrice == null || this.highPrice.isLessThan(price)) {
          this.highPrice = price;
        }
        if (this.lowPrice == null || this.lowPrice.isGreaterThan(price)) {
          this.lowPrice = price;
        }
    }

    @Override
    public String toString() {
        return String.format(
                "{end time: %1s, close price: %2$f, open price: %3$f, low price: %4$f, high price: %5$f, volume: %6$f}",
            this.endTime.withZoneSameInstant(ZoneId.systemDefault()),
            this.closePrice.doubleValue(), this.openPrice.doubleValue(),
            this.lowPrice.doubleValue(), this.highPrice.doubleValue(), this.volume.doubleValue());
    }

    /**
     * @param timePeriod the time period
     * @param endTime    the end time of the bar
     * @throws NullPointerException if one of the arguments is null
     */
    private static void checkTimeArguments(final Duration timePeriod, final ZonedDateTime endTime) {
        Objects.requireNonNull(timePeriod, "Time period cannot be null");
        Objects.requireNonNull(endTime, "End time cannot be null");
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            this.beginTime, this.endTime, this.timePeriod, this.openPrice, this.highPrice,
            this.lowPrice, this.closePrice, this.volume,
            this.amount,
            this.trades
        );
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof final BacktestBar other))
            return false;
      return Objects.equals(this.beginTime, other.beginTime) && Objects.equals(this.endTime, other.endTime)
               && Objects.equals(this.timePeriod, other.timePeriod) && Objects.equals(this.openPrice, other.openPrice)
               && Objects.equals(this.highPrice, other.highPrice) && Objects.equals(this.lowPrice, other.lowPrice)
               && Objects.equals(this.closePrice, other.closePrice) && Objects.equals(this.volume, other.volume)
               && Objects.equals(this.amount, other.amount) && this.trades == other.trades;
    }
}
