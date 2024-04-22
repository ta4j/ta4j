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
package org.ta4j.core.live;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

/**
 * Live trading implementation of a {@link Bar}.
 */
// TODO change to record
public class LiveBar implements Bar {

  /** The time period (e.g. 1 day, 15 min, etc.) of the bar. */
  private final Duration timePeriod;

  /** The begin time of the bar period. */
  private final ZonedDateTime beginTime;

  /** The end time of the bar period. */
  private final ZonedDateTime endTime;

  /** The open price of the bar period. */
  private final Num openPrice;

  /** The high price of the bar period. */
  private final Num highPrice;

  /** The low price of the bar period. */
  private final Num lowPrice;

  /** The close price of the bar period. */
  private final Num closePrice;

  /** The total traded volume of the bar period. */
  private final Num volume;


  /**
   * Constructor.
   *
   * @param timePeriod the time period
   * @param endTime the end time of the bar period
   * @param openPrice the open price of the bar period
   * @param highPrice the highest price of the bar period
   * @param lowPrice the lowest price of the bar period
   * @param closePrice the close price of the bar period
   * @param volume the total traded volume of the bar period
   */
  LiveBar(
      final Duration timePeriod,
      final ZonedDateTime endTime,
      final Num openPrice,
      final Num highPrice,
      final Num lowPrice,
      final Num closePrice,
      final Num volume
  ) {
    checkTimeArguments(timePeriod, endTime);
    this.timePeriod = timePeriod;
    this.endTime = endTime;
    this.beginTime = endTime.minus(timePeriod);
    this.openPrice = openPrice;
    this.highPrice = highPrice;
    this.lowPrice = lowPrice;
    this.closePrice = closePrice;
    this.volume = volume;
  }


  /**
   * @return the time period of the bar (must be the same for all bars within the
   *     same {@code BarSeries})
   */
  @Override
  public Duration getTimePeriod() {
    return this.timePeriod;
  }


  /**
   * @return the begin timestamp of the bar period (derived by {@link #endTime} -
   *     {@link #timePeriod})
   */
  @Override
  public ZonedDateTime getBeginTime() {
    return this.beginTime;
  }


  @Override
  public ZonedDateTime getEndTime() {
    return this.endTime;
  }


  @Override
  public Num getOpenPrice() {
    return this.openPrice;
  }


  @Override
  public Num getHighPrice() {
    return this.highPrice;
  }


  @Override
  public Num getLowPrice() {
    return this.lowPrice;
  }


  @Override
  public Num getClosePrice() {
    return this.closePrice;
  }


  @Override
  public Num getVolume() {
    return this.volume;
  }


  @Override
  public String toString() {
    return String.format(
        "{end time: %1s, close price: %2$f, open price: %3$f, low price: %4$f, high price: %5$f, volume: %6$f}",
        this.endTime.withZoneSameInstant(ZoneId.systemDefault()), this.closePrice.doubleValue(), this.openPrice.doubleValue(),
        this.lowPrice.doubleValue(), this.highPrice.doubleValue(), this.volume.doubleValue()
    );
  }


  /**
   * @param timePeriod the time period
   * @param endTime the end time of the bar
   *
   * @throws NullPointerException if one of the arguments is null
   */
  private static void checkTimeArguments(final Duration timePeriod, final ZonedDateTime endTime) {
    Objects.requireNonNull(timePeriod, "Time period cannot be null");
    Objects.requireNonNull(endTime, "End time cannot be null");
  }
}
