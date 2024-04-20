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
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.MockRule;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.TestUtils;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.average.MMAIndicator;
import org.ta4j.core.indicators.candles.price.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private final ExternalIndicatorTest xls;

    public MMAIndicatorTest(final NumFactory numFunction) {
        super((data, params) -> new MMAIndicator(data, (int) params[0]), numFunction);
        this.xls = new XLSIndicatorTest(this.getClass(), "MMA.xls", 6, numFunction);
    }

    private BacktestBarSeries data;

    @Before
    public void setUp() {
        this.data = new MockBarSeriesBuilder().withNumFactory(this.numFactory)
                .withData(64.75, 63.79, 63.73, 63.73, 63.55, 63.19, 63.91, 63.85, 62.95, 63.37, 61.33, 61.51)
                .build();
    }

    @Test
    public void firstValueShouldBeEqualsToFirstDataValue() {
        final var actualIndicator = getIndicator(new ClosePriceIndicator(this.data), 1);
        this.data.replaceStrategy(new MockStrategy(new MockRule(List.of(actualIndicator))));
        this.data.advance();
        assertEquals(64.75, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void mmaUsingBarCount10UsingClosePrice() {
        final var actualIndicator = getIndicator(new ClosePriceIndicator(this.data), 10);
        this.data.replaceStrategy(new MockStrategy(new MockRule(List.of(actualIndicator))));

        for (int i = 0; i < 10; i++) {
            this.data.advance();
        }

        assertEquals(63.9983, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
        this.data.advance();
        assertEquals(63.7315, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
        this.data.advance();
        assertEquals(63.5093, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void testAgainstExternalData1() throws Exception {
        assertBarCount(1, 329.0);
    }

    @Test
    public void testAgainstExternalData3() throws Exception {
        assertBarCount(3, 327.2900);
    }

    @Test
    public void testAgainstExternalData13() throws Exception {
        assertBarCount(13, 326.9696);
    }

    private void assertBarCount(final int barCount, final double expected) throws Exception {
        final var xlsSeries = this.xls.getSeries();
        final var xlsClose = new ClosePriceIndicator(xlsSeries);
        final var actualIndicator = getIndicator(xlsClose, barCount);
        final var expectedIndicator = this.xls.getIndicator(barCount);
        xlsSeries.replaceStrategy(new MockStrategy(new MockRule(List.of(actualIndicator, expectedIndicator))));

        assertIndicatorEquals(expectedIndicator, actualIndicator);
        assertEquals(expected, actualIndicator.getValue().doubleValue(), TestUtils.GENERAL_OFFSET);
    }

}
