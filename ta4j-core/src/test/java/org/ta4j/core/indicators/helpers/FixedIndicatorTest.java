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
package org.ta4j.core.indicators.helpers;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Assert;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class FixedIndicatorTest {

  @Test
  public void getValueOnFixedDecimalIndicatorDouble() {
    final var series = new MockBarSeriesBuilder().withDefaultData().build();
    final var fixedDecimalIndicator = new FixedDecimalIndicator(series, 13.37, 42, -17);
    series.replaceStrategy(new MockStrategy(fixedDecimalIndicator));

    series.advance();
    assertNumEquals(13.37, fixedDecimalIndicator.getValue());
    series.advance();
    assertNumEquals(42, fixedDecimalIndicator.getValue());
    series.advance();
    assertNumEquals(-17, fixedDecimalIndicator.getValue());

  }


  @Test
  public void getValueOnFixedDecimalIndicatorString() {
    final var series = new MockBarSeriesBuilder().withDefaultData().build();
    final var fixedDecimalIndicator = new FixedDecimalIndicator(series, "3.0", "-123.456", "0.0");
    series.replaceStrategy(new MockStrategy(fixedDecimalIndicator));

    series.advance();
    assertNumEquals("3.0", fixedDecimalIndicator.getValue());
    series.advance();
    assertNumEquals("-123.456", fixedDecimalIndicator.getValue());
    series.advance();
    assertNumEquals("0.0", fixedDecimalIndicator.getValue());

  }


  @Test
  public void getValueOnFixedBooleanIndicator() {
    final var series = new MockBarSeriesBuilder().withDefaultData().build();
    final var fixedBooleanIndicator = new FixedBooleanIndicator(false, false, true, false, true);
    series.replaceStrategy(new MockStrategy(fixedBooleanIndicator));

    series.advance();
    Assert.assertFalse(fixedBooleanIndicator.getValue());
    series.advance();
    Assert.assertFalse(fixedBooleanIndicator.getValue());
    series.advance();
    Assert.assertTrue(fixedBooleanIndicator.getValue());
    series.advance();
    Assert.assertFalse(fixedBooleanIndicator.getValue());
    series.advance();
    Assert.assertTrue(fixedBooleanIndicator.getValue());
  }
}
