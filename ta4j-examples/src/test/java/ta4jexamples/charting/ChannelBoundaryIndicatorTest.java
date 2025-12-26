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
package ta4jexamples.charting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.PriceChannel;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChannelBoundaryIndicator}.
 */
class ChannelBoundaryIndicatorTest {

    private BarSeries series;

    @BeforeEach
    void setUp() {
        series = ChartingTestFixtures.standardDailySeries();
    }

    @Test
    void passesThroughNumValuesFromDelegateIndicator() {
        Num expected = series.numFactory().numOf(42);
        ConstantNumIndicator delegate = new ConstantNumIndicator(series, expected, 3);

        ChannelBoundaryIndicator indicator = new ChannelBoundaryIndicator(delegate, PriceChannel.Boundary.UPPER);

        assertEquals(expected, indicator.getValue(series.getBeginIndex()));
        assertEquals(3, indicator.getCountOfUnstableBars(), "Should delegate unstable bar count");
    }

    @Test
    void extractsBoundaryValuesFromPriceChannelIndicator() {
        Num upper = series.numFactory().numOf(15);
        Num lower = series.numFactory().numOf(5);
        Num median = series.numFactory().numOf(10);
        PriceChannel channel = new TestChannel(upper, lower, median);
        ConstantChannelIndicator delegate = new ConstantChannelIndicator(series, channel, 2);

        ChannelBoundaryIndicator upperIndicator = new ChannelBoundaryIndicator(delegate, PriceChannel.Boundary.UPPER);
        ChannelBoundaryIndicator lowerIndicator = new ChannelBoundaryIndicator(delegate, PriceChannel.Boundary.LOWER);
        ChannelBoundaryIndicator medianIndicator = new ChannelBoundaryIndicator(delegate, PriceChannel.Boundary.MEDIAN);

        assertEquals(upper, upperIndicator.getValue(series.getBeginIndex()));
        assertEquals(lower, lowerIndicator.getValue(series.getBeginIndex()));
        assertEquals(median, medianIndicator.getValue(series.getBeginIndex()));
        assertEquals(2, upperIndicator.getCountOfUnstableBars(), "Should delegate unstable bar count");
    }

    private record TestChannel(Num upper, Num lower, Num median) implements PriceChannel {
    }

    private static final class ConstantNumIndicator implements Indicator<Num> {
        private final Num value;
        private final int unstableBars;
        private final BarSeries series;

        private ConstantNumIndicator(BarSeries series, Num value, int unstableBars) {
            this.series = series;
            this.value = value;
            this.unstableBars = unstableBars;
        }

        @Override
        public Num getValue(int index) {
            return value;
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }
    }

    private static final class ConstantChannelIndicator implements Indicator<PriceChannel> {
        private final PriceChannel channel;
        private final int unstableBars;
        private final BarSeries series;

        private ConstantChannelIndicator(BarSeries series, PriceChannel channel, int unstableBars) {
            this.series = series;
            this.channel = channel;
            this.unstableBars = unstableBars;
        }

        @Override
        public PriceChannel getValue(int index) {
            return channel;
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }
    }
}
