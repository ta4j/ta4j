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

import static org.ta4j.core.TestUtils.assertNext;
import static org.ta4j.core.TestUtils.fastForward;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.average.KAMAIndicator;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * The Class KAMAIndicatorTest.
 *
 * @see <a
 *     href="http://stockcharts.com/school/data/media/chart_school/technical_indicators_and_overlays/kaufman_s_adaptive_moving_average/cs-kama.xls>
 *     http://stockcharts.com/school/data/media/chart_school/technical_indicators_and_overlays/kaufman_s_adaptive_moving_average/cs-kama.xls</a>
 */
public class KAMAIndicatorTest extends AbstractIndicatorTest<Num> {

  private BacktestBarSeries data;


  public KAMAIndicatorTest(final NumFactory numFactory) {
    super(numFactory);
  }


  @Before
  public void setUp() {

    this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
        .withData(110.46, 109.80, 110.17, 109.82, 110.15, 109.31, 109.05, 107.94, 107.76, 109.24, 109.40,
            108.50, 107.96, 108.55, 108.85, 110.44, 109.89, 110.70, 110.79, 110.22, 110.00, 109.27, 106.69,
            107.07, 107.92, 107.95, 107.70, 107.97, 106.09, 106.03, 107.65, 109.54, 110.26, 110.38, 111.94,
            113.59, 113.98, 113.91, 112.62, 112.20, 111.10, 110.18, 111.13, 111.55, 112.08, 111.95, 111.60,
            111.39, 112.25
        )
        .build();
  }


  @Test
  public void kama() {
    final var closePrice = new ClosePriceIndicator(this.data);
    final var kama = new KAMAIndicator(closePrice, 10, 2, 30);
    this.data.replaceStrategy(new MockStrategy(kama));

    fastForward(this.data, 10);

    assertNext(this.data, 109.2400, kama);
    assertNext(this.data, 109.2449, kama);
    assertNext(this.data, 109.2165, kama);
    assertNext(this.data, 109.1173, kama);
    assertNext(this.data, 109.0981, kama);
    assertNext(this.data, 109.0894, kama);
    assertNext(this.data, 109.1240, kama);
    assertNext(this.data, 109.1376, kama);
    assertNext(this.data, 109.2769, kama);
    assertNext(this.data, 109.4365, kama);
    assertNext(this.data, 109.4569, kama);
    assertNext(this.data, 109.4651, kama);
    assertNext(this.data, 109.4612, kama);
    assertNext(this.data, 109.3904, kama);
    assertNext(this.data, 109.3165, kama);
    assertNext(this.data, 109.2924, kama);
    assertNext(this.data, 109.1836, kama);
    assertNext(this.data, 109.0778, kama);
    assertNext(this.data, 108.9498, kama);
    assertNext(this.data, 108.4230, kama);
    assertNext(this.data, 108.0157, kama);
    assertNext(this.data, 107.9967, kama);
    assertNext(this.data, 108.0069, kama);
    assertNext(this.data, 108.2596, kama);
    assertNext(this.data, 108.4818, kama);
    assertNext(this.data, 108.9119, kama);
    assertNext(this.data, 109.6734, kama);
    assertNext(this.data, 110.4947, kama);
    assertNext(this.data, 111.1077, kama);
    assertNext(this.data, 111.4622, kama);
    assertNext(this.data, 111.6092, kama);
    assertNext(this.data, 111.5663, kama);
    assertNext(this.data, 111.5491, kama);
    assertNext(this.data, 111.5425, kama);
    assertNext(this.data, 111.5426, kama);
    assertNext(this.data, 111.5457, kama);
    assertNext(this.data, 111.5658, kama);
    assertNext(this.data, 111.5688, kama);
    assertNext(this.data, 111.5522, kama);
    assertNext(this.data, 111.5595, kama);
  }
}
