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
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.TestUtils.assertNext;
import static org.ta4j.core.TestUtils.fastForward;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SimpleLinearRegressionIndicatorTest extends AbstractIndicatorTest<Num> {

  private NumericIndicator closePrice;
  private BacktestBarSeries data;


  public SimpleLinearRegressionIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(10, 20, 30, 40, 30, 40, 30, 20, 30, 50, 60, 70, 80)
        .build();
    this.closePrice = NumericIndicator.closePrice(this.data);
  }


  @Test
  public void notComputedLinearRegression() {
    final var reg = new SimpleLinearRegressionIndicator(this.closePrice, 1);
    this.data.replaceStrategy(new MockStrategy(reg));
    assertNext(this.data, 0.0, reg);
    assertNext(this.data, 0.0, reg);
    assertNext(this.data, 0.0, reg);
  }


  @Test
  public void calculateLinearRegressionWithLessThan2ObservationsReturnsNaN() {
    final var reg = this.closePrice.simpleLinearRegression(1);
    this.data.replaceStrategy(new MockStrategy(reg));

    assertNext(this.data, 0.0, reg);
    assertNext(this.data, 0.0, reg);
    assertNext(this.data, 0.0, reg);
  }


  @Test
  public void calculateLinearRegressionOn4Observations() {

    final var reg = this.closePrice.simpleLinearRegression(4);
    this.data.replaceStrategy(new MockStrategy(reg));

    final var origReg = buildSimpleRegression(10, 20, 30, 40);
    fastForward(this.data, 4);
    assertNext(this.data, origReg.predict(3), reg);

    origReg.removeData(0, 10);
    origReg.addData(4, 30);
    assertNext(this.data, origReg.predict(3), reg);

    origReg.removeData(1, 20);
    origReg.addData(5, 40);
    assertNext(this.data, origReg.predict(3), reg);

    origReg.removeData(2, 30);
    origReg.addData(6, 30);
    assertNext(this.data, origReg.predict(3), reg);

    origReg.removeData(3, 40);
    origReg.addData(7, 20);
    assertNext(this.data, origReg.predict(3), reg);

    origReg.removeData(4, 30);
    origReg.addData(8, 30);
    assertNext(this.data, origReg.predict(3), reg);

  }


  @Test
  public void calculateLinearRegression() {
    final double[] values = {1, 2, 1.3, 3.75, 2.25};
    final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withData(values).build();
    final var indicator = NumericIndicator.closePrice(series);
    final var reg = indicator.simpleLinearRegression(5);

    final var origReg = buildSimpleRegression(values);
    series.replaceStrategy(new MockStrategy(reg));

    fastForward(series, 5);
    assertNext(series, origReg.predict(4), reg);
  }


  /**
   * @param values values
   *
   * @return a simple linear regression based on provided values
   */
  private static SimpleRegression buildSimpleRegression(final double... values) {
    final var simpleReg = new SimpleRegression();
    for (int i = 0; i < values.length; i++) {
      simpleReg.addData(i, values[i]);
    }
    return simpleReg;
  }
}
