/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.indicators.renko;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RenkoXIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public RenkoXIndicatorTest(final NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void detectsThreeBricksInEitherDirection() {
        var series = buildSeries(100d, 100.6d, 101.2d, 101.8d, 101.2d, 100.6d, 100.0d, 99.4d);
        var indicator = new RenkoXIndicator(new ClosePriceIndicator(series), 0.5d);

        assertThat(indicator.getValue(3)).as("three bullish bricks trigger signal").isTrue();
        assertThat(indicator.getValue(6)).as("three bearish bricks trigger signal").isTrue();
        assertThat(indicator.getValue(4)).as("lull between moves keeps signal active").isTrue();
    }

    @Test
    public void respectsCustomThreshold() {
        var series = buildSeries(100d, 100.6d, 101.2d, 101.8d, 102.4d);
        var indicator = new RenkoXIndicator(new ClosePriceIndicator(series), 0.5d, 4);

        assertThat(indicator.getValue(3)).as("needs four bricks").isFalse();
        assertThat(indicator.getValue(4)).as("four bricks reached").isTrue();
    }

    @Test
    public void countsBricksAtExactBoundaries() {
        var series = buildSeries(100d, 100.5d, 101.0d, 101.5d);
        var indicator = new RenkoXIndicator(new ClosePriceIndicator(series), 0.5d, 3);

        assertThat(indicator.getValue(3)).as("three boundary bricks trigger signal").isTrue();
    }

    @Test
    public void detectsMultipleBricksFromSingleMove() {
        var series = buildSeries(100d, 101.6d, 99.7d);
        var indicator = new RenkoXIndicator(new ClosePriceIndicator(series), 0.5d, 3);

        assertThat(indicator.getValue(1)).as("large bullish move forms bricks").isTrue();
        assertThat(indicator.getValue(2)).as("subsequent bearish move forms bricks").isTrue();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositivePointSize() {
        new RenkoXIndicator(new ClosePriceIndicator(buildSeries(100d, 100.6d)), 0d, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveBrickCount() {
        new RenkoXIndicator(new ClosePriceIndicator(buildSeries(100d, 100.6d)), 0.5d, 0);
    }

    private BarSeries buildSeries(double... closes) {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).add();
        }
        return series;
    }
}
