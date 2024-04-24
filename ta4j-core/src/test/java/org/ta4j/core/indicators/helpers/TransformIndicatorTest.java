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

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TransformIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

  private TransformIndicator transPlus;
  private TransformIndicator transMinus;
  private TransformIndicator transMultiply;
  private TransformIndicator transDivide;
  private TransformIndicator transMax;
  private TransformIndicator transMin;

  private TransformIndicator transAbs;
  private TransformIndicator transPow;
  private TransformIndicator transSqrt;
  private TransformIndicator transLog;
  private BacktestBarSeries series;


  public TransformIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withDefaultData().build();
    final var constantIndicator = new ConstantIndicator<>(this.series, numOf(4));

    this.transPlus = TransformIndicator.plus(constantIndicator, 10);
    this.transMinus = TransformIndicator.minus(constantIndicator, 10);
    this.transMultiply = TransformIndicator.multiply(constantIndicator, 10);
    this.transDivide = TransformIndicator.divide(constantIndicator, 10);
    this.transMax = TransformIndicator.max(constantIndicator, 10);
    this.transMin = TransformIndicator.min(constantIndicator, 10);

    this.transAbs = TransformIndicator.abs(new ConstantIndicator<Num>(this.series, numOf(-4)));
    this.transPow = TransformIndicator.pow(constantIndicator, 2);
    this.transSqrt = TransformIndicator.sqrt(constantIndicator);
    this.transLog = TransformIndicator.log(constantIndicator);

    this.series.replaceStrategy(
        new MockStrategy(
            this.transPlus,
            this.transMinus,
            this.transMultiply,
            this.transDivide,
            this.transMax,
            this.transMin,
            this.transAbs,
            this.transPow,
            this.transSqrt,
            this.transLog
        )
    );
  }


  @Test
  public void getValue() {
    this.series.advance();
    assertNumEquals(14, this.transPlus.getValue());
    assertNumEquals(-6, this.transMinus.getValue());
    assertNumEquals(40, this.transMultiply.getValue());
    assertNumEquals(0.4, this.transDivide.getValue());
    assertNumEquals(10, this.transMax.getValue());
    assertNumEquals(4, this.transMin.getValue());

    assertNumEquals(4, this.transAbs.getValue());
    assertNumEquals(16, this.transPow.getValue());
    assertNumEquals(2, this.transSqrt.getValue());
    assertNumEquals(1.3862943611198906, this.transLog.getValue());
  }
}
