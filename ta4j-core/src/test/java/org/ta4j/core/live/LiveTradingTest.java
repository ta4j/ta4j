/*
 * *
 *  * The MIT License (MIT)
 *  *
 *  * Copyright (c) 2017-2023 Ta4j Organization & respective
 *  * authors (see AUTHORS)
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy of
 *  * this software and associated documentation files (the "Software"), to deal in
 *  * the Software without restriction, including without limitation the rights to
 *  * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *  * the Software, and to permit persons to whom the Software is furnished to do so,
 *  * subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *  * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *  * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *  * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *  * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package org.ta4j.core.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import org.junit.Test;
import org.ta4j.core.backtest.BacktestStrategy;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class LiveTradingTest extends AbstractIndicatorTest<LiveBarSeries, Num> {

  public LiveTradingTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Test
  public void testStandardFlow() {
    final var smaTest = new ArrayList<NumericIndicator>(1);
    final var liveTrading = new LiveTradingBuilder()
        .withNumFactory(this.numFactory)
        .withName("LiveTrading")
        .withStrategyFactory(series -> {
          final var closePrice = NumericIndicator.closePrice(series);
          final var sma = closePrice.sma(5);
          smaTest.add(sma);
          final var entryRule = sma.isGreaterThan(5);
          final var exitRule = sma.isLessThan(11);
          return new BacktestStrategy("LiveSMA", entryRule, exitRule);
        })
        .build();

    assertNull(smaTest.getFirst().getValue());

    final var expectedValues = new Num[] {numOf(2), numOf(4), numOf(6), numOf(8), numOf(10), numOf(10), numOf(10)};
    final var expectedEntries = new boolean[] {false, false, false, false, true, true, true};
    final var expectedExits = new boolean[] {false, false, false, false, true, true, true};
    for (int i = 0; i < 6; i++) {
      liveTrading.barBuilder()
          .timePeriod(Duration.ofMinutes(1))
          .endTime(Instant.now())
          .closePrice(10)
          .add();
      assertEquals(String.valueOf(i), expectedValues[i], smaTest.getFirst().getValue());
      assertEquals(String.valueOf(i), expectedEntries[i], liveTrading.shouldEnter());
      assertEquals(String.valueOf(i), expectedExits[i], liveTrading.shouldExit());
    }

    // test exit rule in stable period
    liveTrading.barBuilder()
        .timePeriod(Duration.ofMinutes(1))
        .endTime(Instant.now())
        .closePrice(100)
        .add();

    assertEquals(numOf(28), smaTest.getFirst().getValue());
    assertFalse(liveTrading.shouldExit());
  }

}
