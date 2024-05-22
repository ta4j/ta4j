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
package org.ta4j.core;

import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertIndicatorNotEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.TestUtils.assertNumNotEquals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.backtest.BacktestStrategy;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TestUtilsTest extends AbstractIndicatorTest<BarSeries, Num> {

  private static final String stringDouble = "1234567890.12345";
  private static final String diffStringDouble = "1234567890.12346";
  private static final BigDecimal bigDecimalDouble = new BigDecimal(stringDouble);
  private static final BigDecimal diffBigDecimalDouble = new BigDecimal(diffStringDouble);
  private static final int aInt = 1234567890;
  private static final int diffInt = 1234567891;
  private static final double aDouble = 1234567890.1234;
  private static final double diffDouble = 1234567890.1235;
  private static Num numStringDouble;
  private static Num diffNumStringDouble;
  private static Num numInt;
  private static Num diffNumInt;
  private static Num numDouble;
  private static Num diffNumDouble;
  private static final AtomicReference<Indicator<Num>> indicator = new AtomicReference<>();
  private static final AtomicReference<Indicator<Num>> diffIndicator = new AtomicReference<>();


  public TestUtilsTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    numStringDouble = numOf(bigDecimalDouble);
    diffNumStringDouble = numOf(diffBigDecimalDouble);
    numInt = numOf(aInt);
    diffNumInt = numOf(diffInt);
    numDouble = numOf(aDouble);
    diffNumDouble = numOf(diffDouble);
    randomSeries(diffIndicator::set);
    randomSeries(indicator::set);
  }


  private BarSeries randomSeries(Consumer<Indicator<Num>> consumer) {
    var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
        .withStrategyFactory(s -> createStrategy(s, consumer))
        .build();
    Instant time = ZonedDateTime.of(1970, 1, 1, 1, 1, 1, 1, ZoneId.systemDefault()).toInstant();
    double random;
    for (int i = 0; i < 1000; i++) {
      random = Math.random();
      time = time.plus(Duration.ofDays(i));
      series.barBuilder()
          .timePeriod(Duration.ofDays(1))
          .endTime(time)
          .openPrice(random)
          .closePrice(random)
          .highPrice(random)
          .lowPrice(random)
          .amount(random)
          .volume(random)
          .trades(0)
          .add();
    }

    return series;
  }


  private BacktestStrategy createStrategy(final BarSeries series, final Consumer<Indicator<Num>> consumer) {
    final var indicator = new ClosePriceIndicator(series);
    consumer.accept(indicator);
    return new MockStrategy(new MockRule(List.of(indicator)));
  }


  @Test
  public void testStringNum() {
    assertNumEquals(stringDouble, numStringDouble);
    assertNumNotEquals(stringDouble, diffNumStringDouble);
    assertNumNotEquals(diffStringDouble, numStringDouble);
    assertNumEquals(diffStringDouble, diffNumStringDouble);
  }


  @Test
  public void testNumNum() {
    assertNumEquals(numStringDouble, numStringDouble);
    assertNumNotEquals(numStringDouble, diffNumStringDouble);
    assertNumNotEquals(diffNumStringDouble, numStringDouble);
    assertNumEquals(diffNumStringDouble, diffNumStringDouble);
  }


  @Test
  public void testIntNum() {
    assertNumEquals(aInt, numInt);
    assertNumNotEquals(aInt, diffNumInt);
    assertNumNotEquals(diffInt, numInt);
    assertNumEquals(diffInt, diffNumInt);
  }


  @Test
  public void testDoubleNum() {
    assertNumEquals(aDouble, numDouble);
    assertNumNotEquals(aDouble, diffNumDouble);
    assertNumNotEquals(diffDouble, numDouble);
    assertNumEquals(diffDouble, diffNumDouble);
  }


  @Test
  public void testIndicatorSame() {
    assertIndicatorEquals(indicator.get(), indicator.get());
  }


  @Test
  public void testIndicatorDifferent1() {
    assertIndicatorNotEquals(indicator.get(), diffIndicator.get());
  }


  @Test
  public void testIndicatorDifferent2() {
    assertIndicatorNotEquals(diffIndicator.get(), indicator.get());
  }


  @Test
  public void testIndicatorSame2() {
    assertIndicatorEquals(diffIndicator.get(), diffIndicator.get());
  }
}
