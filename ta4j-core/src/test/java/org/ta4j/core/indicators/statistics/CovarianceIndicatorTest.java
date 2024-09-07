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

import java.util.LinkedList;
import java.util.Queue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CovarianceIndicatorTest extends AbstractIndicatorTest<Num> {

  private ClosePriceIndicator close;
  private VolumeIndicator volume;
  private BacktestBarSeries data;


  public CovarianceIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory).build();
    this.data.barBuilder().closePrice(6).volume(100).add();
    this.data.barBuilder().closePrice(7).volume(105).add();
    this.data.barBuilder().closePrice(9).volume(130).add();
    this.data.barBuilder().closePrice(12).volume(160).add();
    this.data.barBuilder().closePrice(11).volume(150).add();
    this.data.barBuilder().closePrice(10).volume(130).add();
    this.data.barBuilder().closePrice(11).volume(95).add();
    this.data.barBuilder().closePrice(13).volume(120).add();
    this.data.barBuilder().closePrice(15).volume(180).add();
    this.data.barBuilder().closePrice(12).volume(160).add();
    this.data.barBuilder().closePrice(8).volume(150).add();
    this.data.barBuilder().closePrice(4).volume(200).add();
    this.data.barBuilder().closePrice(3).volume(150).add();
    this.data.barBuilder().closePrice(4).volume(85).add();
    this.data.barBuilder().closePrice(3).volume(70).add();
    this.data.barBuilder().closePrice(5).volume(90).add();
    this.data.barBuilder().closePrice(8).volume(100).add();
    this.data.barBuilder().closePrice(9).volume(95).add();
    this.data.barBuilder().closePrice(11).volume(110).add();
    this.data.barBuilder().closePrice(10).volume(95).add();
    this.close = NumericIndicator.closePrice(this.data);
    this.volume = NumericIndicator.volume(this.data);
  }


  @Test
  public void usingBarCount5UsingClosePriceAndVolume() {
    final var covar = this.close.covariance(this.volume, 5);
    this.data.replaceStrategy(new MockStrategy(covar));

    fastForward(this.data, 5);
    assertNext(this.data, 54.00, covar);
    assertNext(this.data, 32.00, covar);
    assertNext(this.data, 7.20, covar);
    assertNext(this.data, 1.60, covar);
    assertNext(this.data, 31.00, covar);
    assertNext(this.data, 33.60, covar);
    assertNext(this.data, 21.20, covar);
    assertNext(this.data, -48.80, covar);
    assertNext(this.data, 2.80, covar);
    assertNext(this.data, 18.20, covar);
    assertNext(this.data, 23.60, covar);
    assertNext(this.data, -2.20, covar);
    assertNext(this.data, -5.40, covar);
    assertNext(this.data, 20.60, covar);
    assertNext(this.data, 35.40, covar);
    assertNext(this.data, 10.20, covar);
  }


  @Test
  public void firstValueShouldBeZero() {
    final var covar = this.close.covariance(this.volume, 5);
    this.data.replaceStrategy(new MockStrategy(covar));

    assertNext(this.data, 0, covar);
  }


  @Test
  public void shouldBeZeroWhenBarCountIs1() {
    final var covar = this.close.covariance(this.volume, 1);
    this.data.replaceStrategy(new MockStrategy(covar));

    fastForward(this.data, 4);
    assertNext(this.data, 0, covar);
    fastForward(this.data, 5);
    assertNext(this.data, 0, covar);
  }
}

class WindowedCovariance {
  private final int windowSize;
  private final Queue<Double[]> window;
  private double meanX;
  private double meanY;
  private double covariance;


  public WindowedCovariance(final int windowSize) {
    this.windowSize = windowSize;
    this.window = new LinkedList<>();
    this.meanX = 0.0;
    this.meanY = 0.0;
    this.covariance = 0.0;
  }


  public void addDataPoint(final double x, final double y) {
    if (this.window.size() == this.windowSize) {
      final Double[] oldPoint = this.window.poll();
      removeOldPoint(oldPoint[0], oldPoint[1]);
    }

    this.window.offer(new Double[] {x, y});
    updateMeanAndCovariance(x, y);
  }


  private void updateMeanAndCovariance(final double x, final double y) {
    final int n = this.window.size();
    final double deltaX = x - this.meanX;
    final double deltaY = y - this.meanY;

    this.meanX += deltaX / n;
    this.meanY += deltaY / n;

    this.covariance += deltaX * (y - this.meanY);
  }


  private void removeOldPoint(final double x, final double y) {
    final int n = this.window.size() + 1; // before removal, window size was one more
    final double deltaX = x - this.meanX;
    final double deltaY = y - this.meanY;

    this.meanX -= deltaX / n;
    this.meanY -= deltaY / n;

    this.covariance -= deltaX * (y - this.meanY);
  }


  public double getCovariance() {
    final int n = this.window.size();
    if (n > 1) {
      return this.covariance / (n - 1);
    } else {
      return 0.0; // Not enough data points to calculate covariance
    }
  }


  public double getMeanX() {
    return this.meanX;
  }


  public double getMeanY() {
    return this.meanY;
  }


  public static void main(final String[] args) {
    final int windowSize = 5;
    final WindowedCovariance covCalculator = new WindowedCovariance(windowSize);

    final double[] X = {6, 7, 9, 12, 11, 10, 11, 13, 15, 12, 8, 4, 3, 4, 3, 5, 8, 9, 11, 10};
    final double[] Y = {100, 105, 130, 160, 150, 130, 95, 120, 180, 160, 150, 200, 150, 85, 70, 90, 100, 95, 110, 95};

    for (int i = 0; i < X.length; i++) {
      covCalculator.addDataPoint(X[i], Y[i]);
      System.out.println("Covariance for window ending at index " + i + ": " + covCalculator.getCovariance());
    }
  }
}
