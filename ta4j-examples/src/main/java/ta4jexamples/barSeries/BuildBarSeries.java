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
package ta4jexamples.barSeries;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.backtest.BacktestBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;

public class BuildBarSeries {
  private static final Logger LOG = LoggerFactory.getLogger(BuildBarSeries.class);


  /**
   * Calls different functions that shows how a BacktestBarSeries could be created
   * and how Bars could be added
   *
   * @param args command line arguments (ignored)
   */
  @SuppressWarnings("unused")
  public static void main(final String[] args) {
    var a = buildAndAddData();
    a.advance();
    LOG.info("a: {}", a.getBar().closePrice().getName());
    a = buildAndAddData();
    a.advance();
    LOG.info("a: {}", a.getBar().closePrice().getName());
    final BarSeries b = buildWithDouble();
    final BarSeries c = buildWithBigDecimal();
    final BarSeries d = buildManually();
    final BarSeries e = buildManuallyDoubleNum();
    final BarSeries f = buildManuallyAndAddBarManually();
  }


  private static BacktestBarSeries buildAndAddData() {
    final var series = new BacktestBarSeriesBuilder().withName("mySeries").build();

    final Instant endTime = Instant.now();
    // ZonedDateTime endTime, Number openPrice, Number highPrice, Number lowPrice,
    // Number closePrice, volume
    addBars(series, endTime);

    return series;
  }


  private static void addBars(final BacktestBarSeries series, final Instant endTime) {
    series.barBuilder()
        .timePeriod(Duration.ofDays(1))
        .endTime(endTime)
        .openPrice(105.42)
        .highPrice(112.99)
        .lowPrice(104.01)
        .closePrice(111.42)
        .volume(1337)
        .add();
    series.barBuilder()
        .timePeriod(Duration.ofDays(1))
        .endTime(endTime.plus(Duration.ofDays(1)))
        .openPrice(111.43)
        .highPrice(112.83)
        .lowPrice(107.77)
        .closePrice(107.99)
        .volume(1234)
        .add();
    series.barBuilder()
        .timePeriod(Duration.ofDays(1))
        .endTime(endTime.plus(Duration.ofDays(2)))
        .openPrice(107.90)
        .highPrice(117.50)
        .lowPrice(107.90)
        .closePrice(115.42)
        .volume(4242)
        .add();
  }


  private static BarSeries buildWithDouble() {
    final var series = new BacktestBarSeriesBuilder().withName("mySeries")
        .withNumFactory(DoubleNumFactory.getInstance())
        .build();

    final Instant endTime = Instant.now();
    addBars(series, endTime);

    return series;
  }


  private static BarSeries buildWithBigDecimal() {
    final var series = new BacktestBarSeriesBuilder().withName("mySeries")
        .withNumFactory(DecimalNumFactory.getInstance())
        .build();

    final Instant endTime = Instant.now();
    addBars(series, endTime);
    // ...

    return series;
  }


  private static BarSeries buildManually() {
    final var series = new BacktestBarSeriesBuilder().withName("mySeries").build(); // uses BigDecimalNum

    final Instant endTime = Instant.now();
    addBars(series, endTime);
    // ...

    return series;
  }


  private static BarSeries buildManuallyDoubleNum() {
    final var series = new BacktestBarSeriesBuilder().withName("mySeries")
        .withNumFactory(DoubleNumFactory.getInstance())
        .build();
    final Instant endTime = Instant.now();
    addBars(series, endTime);
    // ...

    return series;
  }


  private static BarSeries buildManuallyAndAddBarManually() {
    final var series = new BacktestBarSeriesBuilder().withName("mySeries")
        .withNumFactory(DoubleNumFactory.getInstance())
        .build();

    // create bars and add them to the series. The bars have the same Num type
    // as the series
    final Instant endTime = Instant.now();
    final Bar b1 = series.barBuilder()
        .timePeriod(Duration.ofDays(1))
        .endTime(endTime)
        .openPrice(105.42)
        .highPrice(112.99)
        .lowPrice(104.01)
        .closePrice(111.42)
        .volume(1337.0)
        .build();
    final Bar b2 = series.barBuilder()
        .timePeriod(Duration.ofDays(1))
        .endTime(endTime.plus(Duration.ofDays(1)))
        .openPrice(111.43)
        .highPrice(112.83)
        .lowPrice(107.77)
        .closePrice(107.99)
        .volume(1234.0)
        .build();
    final Bar b3 = series.barBuilder()
        .timePeriod(Duration.ofDays(1))
        .endTime(endTime.plus(Duration.ofDays(2)))
        .openPrice(107.90)
        .highPrice(117.50)
        .lowPrice(107.90)
        .closePrice(115.42)
        .volume(4242.0)
        .build();
    // ...

    series.addBar(b1);
    series.addBar(b2);
    series.addBar(b3);

    return series;
  }
}
