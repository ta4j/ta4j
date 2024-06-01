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
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertNext;
import static org.ta4j.core.TestUtils.fastForward;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.MockRule;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.TestIndicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MMAIndicatorTest extends AbstractIndicatorTest<Num> {

  private final ExternalIndicatorTest xls;


  public MMAIndicatorTest(final NumFactory numFunction) {
    super(numFunction);
    this.xls = new XLSIndicatorTest(this.getClass(), "MMA.xls", 6, numFunction);
  }


  private BacktestBarSeries data;


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(64.75, 63.79, 63.73, 63.73, 63.55, 63.19, 63.91, 63.85, 62.95, 63.37, 61.33, 61.51)
        .build();
  }


  @Test
  public void firstValueShouldBeEqualsToFirstDataValue() {
    final var actualIndicator = NumericIndicator.closePrice(this.data).mma(1);
    this.data.replaceStrategy(new MockStrategy(new MockRule(List.of(actualIndicator))));
    this.data.advance();
    assertEquals(64.75, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }


  @Test
  public void mmaUsingBarCount10UsingClosePrice() {
    final var actualIndicator = NumericIndicator.closePrice(this.data).mma(10);
    this.data.replaceStrategy(new MockStrategy(new MockRule(List.of(actualIndicator))));


    fastForward(this.data, 10);

    assertNext(this.data, 63.9983, actualIndicator);
    assertNext(this.data, 63.7315, actualIndicator);
    assertNext(this.data, 63.5093, actualIndicator);
  }


  @Test
  public void testAgainstExternalData1() throws Exception {
    assertBarCount(1, 329.0);
  }


  @Test
  public void testAgainstExternalData3() throws Exception {
    assertBarCount(3, 327.2900);
  }


  @Test
  public void testAgainstExternalData13() throws Exception {
    assertBarCount(13, 326.9696);
  }


  private void assertBarCount(final int barCount, final double expected) throws Exception {
    final var xlsSeries = this.xls.getSeries();
    final var actualIndicator = NumericIndicator.closePrice(xlsSeries).mma(barCount);
    final var expectedIndicator = this.xls.getIndicator(barCount);
    xlsSeries.replaceStrategy(new MockStrategy(new MockRule(List.of(actualIndicator, expectedIndicator))));

    assertIndicatorEquals(expectedIndicator, new TestIndicator<>(xlsSeries, actualIndicator));
    assertEquals(expected, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }

}
