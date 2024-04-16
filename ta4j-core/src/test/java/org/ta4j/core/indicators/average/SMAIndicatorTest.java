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
package org.ta4j.core.indicators.average;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.MockRule;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.TestUtils;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private final ExternalIndicatorTest xls;

    public SMAIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new SMAIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "SMA.xls", 6, numFactory);
    }

    private BacktestBarSeries data;

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2)
                .build();
    }

    @Test
    public void usingBarCount3UsingClosePrice() {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 3);
        data.addStrategy(new MockStrategy(new MockRule(List.of(indicator))));

        data.advance();
        assertNumEquals((0d + 0d + 1d) / 3, indicator.getValue());
        data.advance();
        assertNumEquals((0d + 1d + 2d) / 3, indicator.getValue());
        data.advance();
        assertNumEquals((1d + 2d + 3d) / 3, indicator.getValue());
        data.advance();
        assertNumEquals(3, indicator.getValue());
        data.advance();
        assertNumEquals(10d / 3, indicator.getValue());
        data.advance();
        assertNumEquals(11d / 3, indicator.getValue());
        data.advance();
        assertNumEquals(4, indicator.getValue());
        data.advance();
        assertNumEquals(13d / 3, indicator.getValue());
        data.advance();
        assertNumEquals(4, indicator.getValue());
        data.advance();
        assertNumEquals(10d / 3, indicator.getValue());
        data.advance();
        assertNumEquals(10d / 3, indicator.getValue());
        data.advance();
        assertNumEquals(10d / 3, indicator.getValue());
        data.advance();
        assertNumEquals(3, indicator.getValue());
    }

    @Test
    public void usingBarCount3UsingClosePriceMovingSerie() {
        data.barBuilder().closePrice(5.).add();

        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 3);
        data.addStrategy(new MockStrategy(new MockRule(List.of(indicator))));
        // unstable bars skipped, unpredictable results

        data.advance();
        assertNumEquals((0d + 0d + 1d) / 3, indicator.getValue());
        data.advance();
        assertNumEquals((0d + 1d + 2d) / 3, indicator.getValue());
        data.advance();
        assertNumEquals((1d + 2d + 3d) / 3, indicator.getValue());
        data.advance();
        assertNumEquals((2d + 3d + 4d) / 3, indicator.getValue());
        data.advance();
        assertNumEquals((3d + 4d + 3d) / 3, indicator.getValue());
        data.advance();
        assertNumEquals((4d + 3d + 4d) / 3, indicator.getValue());
        data.advance();
        assertNumEquals((3d + 4d + 5d) / 3, indicator.getValue());
        data.advance();
        assertNumEquals((4d + 5d + 4d) / 3, indicator.getValue());
        data.advance();
        assertNumEquals((5d + 4d + 3d) / 3, indicator.getValue());
    }

    @Test
    public void whenBarCountIs1ResultShouldBeIndicatorValue() {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        data.addStrategy(new MockStrategy(new MockRule(List.of(indicator))));

        while (data.advance()) {
            assertEquals(data.getBar().getClosePrice(), indicator.getValue());
        }
    }

    @Test
    public void externalData3() throws Exception {
        final var series = xls.getSeries();
        Indicator<Num> xlsClose = new ClosePriceIndicator(series);
        series.addStrategy(new MockStrategy(new MockRule(List.of(xlsClose))));

        var actualIndicator = getIndicator(xlsClose, 3);
        var expectedIndicator = xls.getIndicator(3);
        series.addStrategy(new MockStrategy(new MockRule(List.of(actualIndicator, expectedIndicator))));

        assertIndicatorEquals(expectedIndicator, actualIndicator);
        assertEquals(326.6333, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void externalData1() throws Exception {
        final var series = xls.getSeries();
        Indicator<Num> xlsClose = new ClosePriceIndicator(series);

        var actualIndicator = getIndicator(xlsClose, 1);
        var expectedIndicator = xls.getIndicator(1);

        series.addStrategy(new MockStrategy(new MockRule(List.of(actualIndicator, expectedIndicator))));

        assertIndicatorEquals(expectedIndicator, actualIndicator);
        assertEquals(329.0, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void externalData13() throws Exception {
        final var series = xls.getSeries();
        Indicator<Num> xlsClose = new ClosePriceIndicator(series);
        series.addStrategy(new MockStrategy(new MockRule(List.of(xlsClose))));

        var actualIndicator = getIndicator(xlsClose, 13);
        var expectedIndicator = xls.getIndicator(13);
        series.addStrategy(new MockStrategy(new MockRule(List.of(actualIndicator, expectedIndicator))));

        assertIndicatorEquals(expectedIndicator, actualIndicator);
        assertEquals(327.7846, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
    }

}
