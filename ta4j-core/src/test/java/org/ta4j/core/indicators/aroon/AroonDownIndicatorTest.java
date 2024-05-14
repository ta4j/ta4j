/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockRule;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AroonDownIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BacktestBarSeries data;

    public AroonDownIndicatorTest(final NumFactory numFunction) {
        super(null, numFunction);
    }

    @Before
    public void init() {
      this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withName("Aroon data").build();
        // FB, daily, 9.19.'17                       // barcount before
      this.data.barBuilder().lowPrice(167.15).add(); // 0 -> unstable
      this.data.barBuilder().lowPrice(168.20).add(); // 1 -> unstable
      this.data.barBuilder().lowPrice(166.41).add(); // 0 -> unstable
      this.data.barBuilder().lowPrice(166.18).add(); // 0 -> unstable
      this.data.barBuilder().lowPrice(166.33).add(); // 1 -> unstable
      this.data.barBuilder().lowPrice(165.00).add(); // 0 -> (5 - 0) / 5 * 100 = 100
      this.data.barBuilder().lowPrice(167.63).add(); // 1 -> (5 - 1) / 5 * 100 = 80
      this.data.barBuilder().lowPrice(171.97).add(); // 2 -> (5 - 2) / 5 * 100 = 60
      this.data.barBuilder().lowPrice(171.31).add(); // 3 -> (5 - 3) / 5 * 100 = 40
      this.data.barBuilder().lowPrice(169.55).add(); // 4 -> (5 - 4) / 5 * 100 = 20
      this.data.barBuilder().lowPrice(169.57).add(); // 5 -> (5 - 5) / 5 * 100 = 0
      this.data.barBuilder().lowPrice(170.27).add();
      this.data.barBuilder().lowPrice(170.80).add();
      this.data.barBuilder().lowPrice(172.20).add();
      this.data.barBuilder().lowPrice(175.00).add();
      this.data.barBuilder().lowPrice(172.06).add();
      this.data.barBuilder().lowPrice(170.50).add();
      this.data.barBuilder().lowPrice(170.26).add();
      this.data.barBuilder().lowPrice(169.34).add();
      this.data.barBuilder().lowPrice(170.36).add();

    }

    @Test
    public void upDownAndHigh() {
        final var aroonDownIndicator = new AroonDownIndicator(this.data, 5);
      this.data.replaceStrategy(new MockStrategy(new MockRule(List.of(aroonDownIndicator))));

      for (int i = 0; i < 6; i++) {
        this.data.advance();
      }

      assertNumEquals(100, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(80, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(60, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(40, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(20, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(0, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(0, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(40, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(20, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(0, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(0, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(0, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(100, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(100, aroonDownIndicator.getValue());
      this.data.advance();
      assertNumEquals(80, aroonDownIndicator.getValue());
    }

    @Test
    public void onlyNaNValues() {
        final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withName("NaN test").build();
        for (long i = 0; i <= 1000; i++) {
            series.barBuilder().openPrice(NaN).closePrice(NaN).highPrice(NaN).lowPrice(NaN).volume(NaN).add();
        }

        final var aroonDownIndicator = new AroonDownIndicator(series, 5);
      series.replaceStrategy(new MockStrategy(new MockRule(List.of(aroonDownIndicator))));

        while (series.advance()) {
            assertEquals(NaN.toString(), aroonDownIndicator.getValue().toString());
        }
    }

    @Test
    public void naNValuesInInterval() {
        final var series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).withName("NaN test").build();
        for (long i = 10; i >= 0; i--) { // (10, NaN, 9, NaN, 8, NaN, 7, NaN)
            final Num lowPrice = i % 2 == 0 ? series.numFactory().numOf(i) : NaN;
            series.barBuilder().lowPrice(lowPrice).add();
        }
        series.barBuilder().lowPrice(numOf(10d)).add();

        final var aroonDownIndicator = new AroonDownIndicator(series, 5);
      series.replaceStrategy(new MockStrategy(new MockRule(List.of(aroonDownIndicator))));

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
          series.advance();
            if (i % 2 != 0 && i < 11) {
              assertEquals(NaN.toString(), aroonDownIndicator.getValue().toString());
            } else if (i < 11) {
              assertNumEquals(series.numFactory().hundred().toString(), aroonDownIndicator.getValue());
            }  else {
              assertNumEquals(series.numFactory().numOf(80).toString(), aroonDownIndicator.getValue());
            }
        }
    }
}
