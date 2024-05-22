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
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.MockRule;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.TestUtils;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ATRIndicatorTest extends AbstractIndicatorTest<BarSeries, Num> {

  private final ExternalIndicatorTest xls;


  public ATRIndicatorTest(final NumFactory numFactory) {
    super((data, params) -> new ATRIndicator(data, (int) params[0]), numFactory);
    this.xls = new XLSIndicatorTest(this.getClass(), "ATR.xls", 7, numFactory);
  }


  @Test
  public void testDummy() {
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).build();
    series.barBuilder()
        .openPrice(0)
        .closePrice(12)
        .highPrice(15)
        .lowPrice(8)
        .amount(0)
        .volume(0)
        .add();
    series.barBuilder()
        .openPrice(0)
        .closePrice(8)
        .highPrice(11)
        .lowPrice(6)
        .volume(0)
        .amount(0)
        .trades(0)
        .add();
    series.barBuilder()
        .openPrice(0)
        .closePrice(15)
        .highPrice(17)
        .lowPrice(14)
        .volume(0)
        .amount(0)
        .trades(0)
        .add();
    series.barBuilder()
        .openPrice(0)
        .closePrice(15)
        .highPrice(17)
        .lowPrice(14)
        .volume(0)
        .amount(0)
        .trades(0)
        .add();
    series.barBuilder()
        .openPrice(0)
        .closePrice(0)
        .highPrice(0)
        .lowPrice(2)
        .volume(0)
        .amount(0)
        .trades(0)
        .add();
    final Indicator<Num> indicator = getIndicator(series, 3);
    series.replaceStrategy(new MockStrategy(new MockRule(List.of(indicator))));

    series.advance();
    var value = indicator.getValue().doubleValue();
    assertEquals(7d, value, TestUtils.GENERAL_OFFSET);

    series.advance();
    assertEquals(6d / 3 + (1 - 1d / 3) * value, indicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
    value = indicator.getValue().doubleValue();

    series.advance();
    assertEquals(9d / 3 + (1 - 1d / 3) * value, indicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
    value = indicator.getValue().doubleValue();

    series.advance();
    assertEquals(3d / 3 + (1 - 1d / 3) * value, indicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
    value = indicator.getValue().doubleValue();

    series.advance();
    assertEquals(15d / 3 + (1 - 1d / 3) * value, indicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }


  @Test
  public void testXls1() throws Exception {
    final var xlsSeries = this.xls.getSeries();
    final var indicator = getIndicator(xlsSeries, 1);
    final var expected = this.xls.getIndicator(1);
    xlsSeries.replaceStrategy(new MockStrategy(new MockRule(List.of(expected, indicator))));

    assertIndicatorEquals(expected, indicator);
    assertEquals(4.8, indicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }


  @Test
  public void testXls3() throws Exception {
    final var xlsSeries = this.xls.getSeries();
    final var indicator = getIndicator(xlsSeries, 3);
    final var expected = this.xls.getIndicator(3);
    xlsSeries.replaceStrategy(new MockStrategy(new MockRule(List.of(expected, indicator))));

    assertIndicatorEquals(expected, indicator);
    assertEquals(7.4225, indicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }


  @Test
  public void testXls13() throws Exception {
    final var xlsSeries = this.xls.getSeries();
    final var indicator = getIndicator(xlsSeries, 13);
    final var expected = this.xls.getIndicator(13);
    xlsSeries.replaceStrategy(new MockStrategy(new MockRule(List.of(expected, indicator))));

    assertIndicatorEquals(expected, indicator);
    assertEquals(8.8082, indicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }

}
