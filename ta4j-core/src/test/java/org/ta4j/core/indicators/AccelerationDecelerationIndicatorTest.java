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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.MockRule;
import org.ta4j.core.MockStrategy;
import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AccelerationDecelerationIndicatorTest extends AbstractIndicatorTest<Num> {

    private BacktestBarSeries series;

    public AccelerationDecelerationIndicatorTest(final NumFactory numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        this.series = new MockBarSeriesBuilder().build();

        for (int i = 0; i < 6; i++) {
            this.series.barBuilder().highPrice(i + 1).lowPrice(i + 1).add();
        }
    }

    @Test
    public void calculateWithSma2AndSma3() {
        final var shortBarCount = 2;
        final var longBarCount = 3;
        final var acceleration = new AccelerationDecelerationIndicator(this.series, shortBarCount, longBarCount);
        this.series.replaceStrategy(new MockStrategy(new MockRule(List.of(acceleration))));

        this.series.advance();
        assertFalse(acceleration.isStable());

        final var shortSma1 = (0 + 1.) / shortBarCount;
        final var longSma1 = (0 + 0 + 1.) / longBarCount;
        final var awesome1 = shortSma1 - longSma1;
        final var awesomeSma1 = (0. + awesome1) / shortBarCount;
        final var acceleration1 = awesome1 - awesomeSma1;
        assertNumEquals(acceleration1, acceleration.getValue());

        this.series.advance();
        assertFalse(acceleration.isStable());

        final var shortSma2 = (1. + 2.) / shortBarCount;
        final var longSma2 = (0 + 1. + 2.) / longBarCount;
        final var awesome2 = shortSma2 - longSma2;
        final var awesomeSma2 = (awesome2 + awesome1) / shortBarCount;
        final var acceleration2 = awesome2 - awesomeSma2;
        assertNumEquals(acceleration2, acceleration.getValue());

        this.series.advance();
        assertTrue(acceleration.isStable());

        final var shortSma3 = (2. + 3.) / shortBarCount;
        final var longSma3 = (1. + 2. + 3.) / longBarCount;
        final var awesome3 = shortSma3 - longSma3;
        final var awesomeSma3 = (awesome3 + awesome2) / shortBarCount;
        final var acceleration3 = awesome3 - awesomeSma3;

        assertNumEquals(acceleration3, acceleration.getValue());
    }

    @Test
    public void withSma1AndSma2() {
        final var acceleration = new AccelerationDecelerationIndicator(this.series, 1, 2);
        this.series.replaceStrategy(new MockStrategy(new MockRule(List.of(acceleration))));
        this.series.advance();
        assertNumEquals(0, acceleration.getValue());
        this.series.advance();
        assertNumEquals(0, acceleration.getValue());
        this.series.advance();
        assertNumEquals(0, acceleration.getValue());
        this.series.advance();
        assertNumEquals(0, acceleration.getValue());
        this.series.advance();
        assertNumEquals(0, acceleration.getValue());
    }
}
