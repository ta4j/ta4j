/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Lukáš Kvídera
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
package org.ta4j.core.indicators.bool;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.fastForward;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class InSlopeIndicatorTest {

  private InSlopeIndicator rulePositiveSlope;
  private InSlopeIndicator ruleNegativeSlope;
  private BacktestBarSeries data;


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withData(50, 70, 80, 90, 99, 60, 30, 20, 10, 0).build();
    final var indicator = NumericIndicator.closePrice(this.data);

    this.rulePositiveSlope = new InSlopeIndicator(
        indicator,
        this.data.numFactory().numOf(20),
        this.data.numFactory().numOf(30)
    );
    this.ruleNegativeSlope = new InSlopeIndicator(
        indicator,
        this.data.numFactory().numOf(-40),
        this.data.numFactory().numOf(-20)
    );

    this.data.replaceStrategy(new MockStrategy(indicator, this.ruleNegativeSlope, this.rulePositiveSlope));
  }


  @Test
  public void isSatisfied() {
    fastForward(this.data, 2);
    assertFalse(this.rulePositiveSlope.toRule().isSatisfied());
    assertFalse(this.ruleNegativeSlope.toRule().isSatisfied());
    fastForward(this.data, 2);
    assertTrue(this.rulePositiveSlope.toRule().isSatisfied());
    assertFalse(this.ruleNegativeSlope.toRule().isSatisfied());
    fastForward(this.data, 2);
    assertFalse(this.rulePositiveSlope.toRule().isSatisfied());
    fastForward(this.data, 4);
    assertTrue(this.ruleNegativeSlope.toRule().isSatisfied());
    fastForward(this.data, 3);
    assertFalse(this.rulePositiveSlope.toRule().isSatisfied());
    assertFalse(this.ruleNegativeSlope.toRule().isSatisfied());

  }
}
