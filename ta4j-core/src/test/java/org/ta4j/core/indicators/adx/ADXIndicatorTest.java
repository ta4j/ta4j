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
package org.ta4j.core.indicators.adx;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.TestIndicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ADXIndicatorTest extends AbstractIndicatorTest<Num> {

  private final ExternalIndicatorTest xls;


  public ADXIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
    this.xls = new XLSIndicatorTest(this.getClass(), "ADX.xls", 15, this.numFactory);
  }


  @Test
  public void externalData11() throws Exception {
    final var series = this.xls.getSeries();
    final var actualIndicator = NumericIndicator.adx(series, 1, 1);
    final var expectedIndicator = this.xls.getIndicator(1, 1);
    series.replaceStrategy(new MockStrategy(expectedIndicator, actualIndicator));

    assertIndicatorEquals(expectedIndicator, new TestIndicator<>(series, actualIndicator));
    assertEquals(100.0, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }


  @Test
  public void externalData32() throws Exception {
    final var series = this.xls.getSeries();
    final var actualIndicator = NumericIndicator.adx(series, 3, 2);
    final var expectedIndicator = this.xls.getIndicator(3, 2);
    series.replaceStrategy(new MockStrategy(expectedIndicator, actualIndicator));

    assertIndicatorEquals(expectedIndicator, new TestIndicator<>(series, actualIndicator));
    assertEquals(12.1330, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }


  @Test
  public void externalData138() throws Exception {
    final var series = this.xls.getSeries();
    final var actualIndicator = NumericIndicator.adx(series, 13, 8);
    final var expectedIndicator = this.xls.getIndicator(13, 8);
    series.replaceStrategy(new MockStrategy(expectedIndicator, actualIndicator));

    assertIndicatorEquals(expectedIndicator, new TestIndicator<>(series, actualIndicator));
    assertEquals(7.3884, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }

}
