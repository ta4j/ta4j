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
package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertNextFalse;
import static org.ta4j.core.TestUtils.assertNextTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ChandelierExitLongIndicatorTest extends AbstractIndicatorTest<Num> {

  private BacktestBarSeries data;


  public ChandelierExitLongIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory).build();
    this.data.barBuilder().lowPrice(99).highPrice(101).closePrice(100).add();
    this.data.barBuilder().lowPrice(104).highPrice(106).closePrice(105).add();
    this.data.barBuilder().lowPrice(109).highPrice(111).closePrice(110).add();
    this.data.barBuilder().lowPrice(107).highPrice(109).closePrice(108).add();
    this.data.barBuilder().lowPrice(92).highPrice(96).closePrice(90).add(); // this is out of 3 * atr
    this.data.barBuilder().lowPrice(103).highPrice(105).closePrice(104).add();
    this.data.barBuilder().lowPrice(105).highPrice(107).closePrice(106).add();
  }


  @Test
  public void massIndexUsing3And8BarCounts() {
    final var cel = new ChandelierExitLongIndicator(this.data, 5, 2);
    this.data.replaceStrategy(new MockStrategy(cel));

    assertNextFalse(this.data, cel);
    assertNextFalse(this.data, cel);
    assertNextFalse(this.data, cel);
    assertNextFalse(this.data, cel);
    assertNextTrue(this.data, cel);
    assertNextFalse(this.data, cel);
    assertNextFalse(this.data, cel);
  }
}
