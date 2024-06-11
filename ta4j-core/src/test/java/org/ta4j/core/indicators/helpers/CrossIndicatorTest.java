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

import static org.ta4j.core.TestUtils.assertNextFalse;
import static org.ta4j.core.TestUtils.assertNextTrue;
import static org.ta4j.core.TestUtils.fastForward;

import org.junit.jupiter.api.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class CrossIndicatorTest {

  @Test
  void testCrossedConstant() {
    final var series = new MockBarSeriesBuilder().withDefaultData().build();
    final var crossIndicator = NumericIndicator.closePrice(series).crossedOver(5);

    series.replaceStrategy(new MockStrategy(crossIndicator));

    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextTrue(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
  }


  @Test
  void testCrossedIndicator() {
    final var series = new MockBarSeriesBuilder().withData(8, 7, 5, 2, 5, 6, 7, 8, 9, 10).build();
    final var sma = NumericIndicator.closePrice(series).sma(2);
    final var crossIndicator = NumericIndicator.closePrice(series).crossedOver(sma);

    series.replaceStrategy(new MockStrategy(crossIndicator));

    fastForward(series, 2);
    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextTrue(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
    assertNextFalse(series, crossIndicator);
  }

}
