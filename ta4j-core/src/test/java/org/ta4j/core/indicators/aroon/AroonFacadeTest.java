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
package org.ta4j.core.indicators.aroon;

import static org.junit.Assert.assertEquals;
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

public class AroonFacadeTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

  private BacktestBarSeries data;


  public AroonFacadeTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void init() {
    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withName("Aroon data").build();
    this.data.barBuilder()
        .openPrice(168.28)
        .closePrice(169.87)
        .highPrice(167.15)
        .lowPrice(169.64)
        .volume(0)
        .add();

    this.data.barBuilder()
        .openPrice(168.84)
        .closePrice(169.36)
        .highPrice(168.20)
        .lowPrice(168.71)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(168.88)
        .closePrice(169.29)
        .highPrice(166.41)
        .lowPrice(167.74)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(168.00)
        .closePrice(168.38)
        .highPrice(166.18)
        .lowPrice(166.32)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(166.89)
        .closePrice(167.70)
        .highPrice(166.33)
        .lowPrice(167.24)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(165.25)
        .closePrice(168.43)
        .highPrice(165)
        .lowPrice(168.05)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(168.17)
        .closePrice(170.18)
        .highPrice(167.63)
        .lowPrice(169.92)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(170.42)
        .closePrice(172.15)
        .highPrice(170.06)
        .lowPrice(171.97)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(172.41)
        .closePrice(172.92)
        .highPrice(171.31)
        .lowPrice(172.02)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(171.2)
        .closePrice(172.39)
        .highPrice(169.55)
        .lowPrice(170.72)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(170.91)
        .closePrice(172.48)
        .highPrice(169.57)
        .lowPrice(172.09)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(171.8)
        .closePrice(173.31)
        .highPrice(170.27)
        .lowPrice(173.21)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(173.09)
        .closePrice(173.49)
        .highPrice(170.8)
        .lowPrice(170.95)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(172.41)
        .closePrice(173.89)
        .highPrice(172.2)
        .lowPrice(173.51)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(173.87)
        .closePrice(174.17)
        .highPrice(175)
        .lowPrice(96)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(173)
        .closePrice(173.17)
        .highPrice(172.06)
        .lowPrice(173.05)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(172.26)
        .closePrice(172.28)
        .highPrice(170.5)
        .lowPrice(170.96)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(170.88)
        .closePrice(172.34)
        .highPrice(170.26)
        .lowPrice(171.64)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(171.85)
        .closePrice(172.07)
        .highPrice(169.34)
        .lowPrice(170.01)
        .volume(0)
        .add();
    this.data.barBuilder()
        .openPrice(170.75)
        .closePrice(172.56)
        .highPrice(170.36)
        .lowPrice(172.52)
        .volume(0)
        .add(); // FB, daily, 9.19.'17

  }


  @Test
  public void testCreation() {
    final AroonFacade facade = new AroonFacade(this.data, 5);
    assertEquals(this.data, facade.down().getBarSeries());
  }


  @Test
  public void testNumericFacadesSameAsDefaultIndicators() {
    final var aroonDownIndicator = new AroonDownIndicator(this.data, 5);
    final var aroonUpIndicator = new AroonUpIndicator(this.data, 5);
    final var aroonOscillatorIndicator = new AroonOscillatorIndicator(this.data, 5);

    final var facade = new AroonFacade(this.data, 5);
    final var aroonUpNumeric = facade.up();
    final var aroonDownNumeric = facade.down();
    final var oscillatorNumeric = facade.oscillator();

    this.data.replaceStrategy(
        new MockStrategy(
            aroonUpNumeric,
            aroonDownIndicator,
            aroonUpIndicator,
            aroonDownNumeric
        )
    );

    while (this.data.advance()) {
      assertNumEquals(aroonDownIndicator.getValue(), aroonDownNumeric.getValue());
      assertNumEquals(aroonUpIndicator.getValue(), aroonUpNumeric.getValue());
      assertNumEquals(aroonOscillatorIndicator.getValue(), oscillatorNumeric.getValue());
    }
  }
}
