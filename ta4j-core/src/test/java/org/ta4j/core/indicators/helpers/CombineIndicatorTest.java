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

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CombineIndicatorTest extends AbstractIndicatorTest<Num> {

  private CombineIndicator combinePlus;
  private CombineIndicator combineMinus;
  private CombineIndicator combineMultiply;
  private CombineIndicator combineDivide;
  private CombineIndicator combineMax;
  private CombineIndicator combineMin;
  private BacktestBarSeries series;


  public CombineIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withDefaultData().build();
    final var constantIndicator = new ConstantNumericIndicator(numOf(4));
    final var constantIndicatorTwo = new ConstantNumericIndicator(numOf(2));

    this.combinePlus = CombineIndicator.plus(constantIndicator, constantIndicatorTwo);
    this.combineMinus = CombineIndicator.minus(constantIndicator, constantIndicatorTwo);
    this.combineMultiply = CombineIndicator.multiply(constantIndicator, constantIndicatorTwo);
    this.combineDivide = CombineIndicator.divide(constantIndicator, constantIndicatorTwo);
    this.combineMax = CombineIndicator.max(constantIndicator, constantIndicatorTwo);
    this.combineMin = CombineIndicator.min(constantIndicator, constantIndicatorTwo);
    this.series.replaceStrategy(new MockStrategy(
        this.combinePlus,
        this.combineMinus,
        this.combineMultiply,
        this.combineDivide,
        this.combineMax,
        this.combineMin
    ));
  }


  @Test
  public void testAllDefaultMathCombineFunctions() {
    this.series.advance();
    assertNumEquals(6, this.combinePlus.getValue());
    assertNumEquals(2, this.combineMinus.getValue());
    assertNumEquals(8, this.combineMultiply.getValue());
    assertNumEquals(2, this.combineDivide.getValue());
    assertNumEquals(4, this.combineMax.getValue());
    assertNumEquals(2, this.combineMin.getValue());
  }


  @Test
  public void testDifferenceIndicator() {

    final Function<Number, Num> numFunction = DecimalNum::valueOf;

    final var series = new MockBarSeriesBuilder().withDefaultData().build();
    final var mockIndicator = new FixedDecimalIndicator(
        series,
        -2.0,
        0.00,
        1.00,
        2.53,
        5.87,
        6.00,
        10.0
    );
    final var constantIndicator = new ConstantNumericIndicator(numFunction.apply(6));
    final var differenceIndicator = CombineIndicator.minus(constantIndicator, mockIndicator);
    series.replaceStrategy(new MockStrategy(constantIndicator, differenceIndicator));

    series.advance();
    assertNumEquals("8", differenceIndicator.getValue());
    series.advance();
    assertNumEquals("6", differenceIndicator.getValue());
    series.advance();
    assertNumEquals("5", differenceIndicator.getValue());
    series.advance();
    assertNumEquals("3.47", differenceIndicator.getValue());
    series.advance();
    assertNumEquals("0.13", differenceIndicator.getValue());
    series.advance();
    assertNumEquals("0", differenceIndicator.getValue());
    series.advance();
    assertNumEquals("-4", differenceIndicator.getValue());
  }
}
