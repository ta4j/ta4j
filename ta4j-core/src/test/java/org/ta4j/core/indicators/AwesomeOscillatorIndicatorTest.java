/**
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

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.indicators.numeric.oscilators.AwesomeOscillatorIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AwesomeOscillatorIndicatorTest extends AbstractIndicatorTest<Num> {
    private BacktestBarSeries series;

    public AwesomeOscillatorIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {

        this.series = new MockBarSeriesBuilder().withNumFactory(this.numFactory).build();
        this.series.barBuilder().openPrice(0).closePrice(0).highPrice(16).lowPrice(8).add();
        this.series.barBuilder().openPrice(0).closePrice(0).highPrice(12).lowPrice(6).add();
        this.series.barBuilder().openPrice(0).closePrice(0).highPrice(18).lowPrice(14).add();
        this.series.barBuilder().openPrice(0).closePrice(0).highPrice(10).lowPrice(6).add();
        this.series.barBuilder().openPrice(0).closePrice(0).highPrice(8).lowPrice(4).add();

    }

    @Test
    public void calculateWithSma2AndSma3() {
        final var awesome = new AwesomeOscillatorIndicator(new MedianPriceIndicator(this.series), 2, 3);
        this.series.replaceStrategy(new MockStrategy(awesome));
        // pass to stable range
        this.series.advance();
        this.series.advance();
        assertValues(this.series, 1d / 6, awesome);
        assertValues(this.series, 1, awesome);
        assertValues(this.series, -3, awesome);
    }

    @Test
    public void withSma1AndSma2() {
        final var awesome = new AwesomeOscillatorIndicator(new MedianPriceIndicator(this.series), 1, 2);
        this.series.replaceStrategy(new MockStrategy(awesome));
        // pass to stable range
        this.series.advance();
        assertValues(this.series, -1.5, awesome);
        assertValues(this.series, 3.5, awesome);
        assertValues(this.series, -4, awesome);
        assertValues(this.series, -1, awesome);
    }

    private static void assertValues(final BacktestBarSeries series, final double expected,
            final AwesomeOscillatorIndicator awesome) {
        series.advance();
        assertNumEquals(expected, awesome.getValue());
    }

}
