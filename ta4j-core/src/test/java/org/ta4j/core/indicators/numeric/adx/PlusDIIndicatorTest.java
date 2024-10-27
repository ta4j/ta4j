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
package org.ta4j.core.indicators.numeric.adx;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.TestIndicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PlusDIIndicatorTest extends AbstractIndicatorTest<Num> {

  private final ExternalIndicatorTest xls;


  public PlusDIIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
    this.xls = new XLSIndicatorTest(this.getClass(), "ADX.xls", 12, numFactory);
  }


  @Test
  public void testAgainstExternalData1() throws Exception {
    assertXlsValues(1, 12.5);

  }


  @Test
  public void testAgainstExternalData3() throws Exception {
    assertXlsValues(3, 22.8407);
  }


  @Test
  public void testAgainstExternalData13() throws Exception {
    assertXlsValues(13, 22.1399);
  }


  private void assertXlsValues(final int x, final double expected) throws Exception {
    final var xlsSeries = this.xls.getSeries();
    final var actualIndicator = new PlusDIIndicator(xlsSeries, x);
    final var expectedIndicator = this.xls.getIndicator(x);
    xlsSeries.replaceStrategy(new MockStrategy(actualIndicator, expectedIndicator));

    assertIndicatorEquals(expectedIndicator, new TestIndicator<>(xlsSeries, actualIndicator));
    assertEquals(expected, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
  }
}
