/**
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
import java.time.ZonedDateTime;

import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * A builder to build a new {@code LiveBar} with conversion from a
 * {@link Number} of type {@code T} to a {@link Num Num implementation}.
 */
class LiveBarBuilder implements BarBuilder {

  private final NumFactory numFactory;
  private final BarSeries series;
  private Duration timePeriod;
  private ZonedDateTime endTime;
  private Num openPrice;
  private Num highPrice;
  private Num lowPrice;
  private Num closePrice;
  private Num volume;


  public LiveBarBuilder(final BarSeries series) {
    this.numFactory = series.numFactory();
    this.series = series;
  }


  public LiveBarBuilder timePeriod(final Duration timePeriod) {
    this.timePeriod = timePeriod;
    return this;
  }

  public LiveBarBuilder endTime(final ZonedDateTime endTime) {
    this.endTime = endTime;
    return this;
  }


  /**
   * @param openPrice the open price of the bar period
   *
   * @return {@code this}
   */
  public LiveBarBuilder openPrice(final Number openPrice) {
    this.openPrice = this.numFactory.numOf(openPrice);
    return this;
  }


  /**
   * @param openPrice the open price of the bar period
   *
   * @return {@code this}
   */
  public LiveBarBuilder openPrice(final String openPrice) {
    this.openPrice = this.numFactory.numOf(openPrice);
    return this;
  }


  /**
   * @param highPrice the highest price of the bar period
   *
   * @return {@code this}
   */
  public LiveBarBuilder highPrice(final Number highPrice) {
    this.highPrice = this.numFactory.numOf(highPrice);
    return this;
  }


  /**
   * @param highPrice the highest price of the bar period
   *
   * @return {@code this}
   */
  public LiveBarBuilder highPrice(final String highPrice) {
    this.highPrice = this.numFactory.numOf(highPrice);
    return this;
  }


  /**
   * @param lowPrice the lowest price of the bar period
   *
   * @return {@code this}
   */
  public LiveBarBuilder lowPrice(final Number lowPrice) {
    this.lowPrice = this.numFactory.numOf(lowPrice);
    return this;
  }


  /**
   * @param lowPrice the lowest price of the bar period
   *
   * @return {@code this}
   */
  public LiveBarBuilder lowPrice(final String lowPrice) {
    this.lowPrice = this.numFactory.numOf(lowPrice);
    return this;
  }


  /**
   * @param closePrice the close price of the bar period
   *
   * @return {@code this}
   */
  public LiveBarBuilder closePrice(final Number closePrice) {
    this.closePrice = this.numFactory.numOf(closePrice);
    return this;
  }


  /**
   * @param closePrice the close price of the bar period
   *
   * @return {@code this}
   */
  public LiveBarBuilder closePrice(final String closePrice) {
    this.closePrice = this.numFactory.numOf(closePrice);
    return this;
  }


  /**
   * @param volume the total traded volume of the bar period
   *
   * @return {@code this}
   */
  public LiveBarBuilder volume(final Number volume) {
    this.volume = this.numFactory.numOf(volume);
    return this;
  }


  /**
   * @param volume the total traded volume of the bar period
   *
   * @return {@code this}
   */
  public LiveBarBuilder volume(final String volume) {
    this.volume = this.numFactory.numOf(volume);
    return this;
  }


  public LiveBar build() {
    return new LiveBar(
        this.timePeriod,
        this.endTime.minus(this.timePeriod),
        this.endTime,
        this.openPrice,
        this.highPrice,
        this.lowPrice,
        this.closePrice,
        this.volume
    );
  }

  public void add() {
    this.series.addBar(build());
  }
}
