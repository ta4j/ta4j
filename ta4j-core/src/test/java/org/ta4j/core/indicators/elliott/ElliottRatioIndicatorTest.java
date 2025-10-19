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
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottRatio.RatioType;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class ElliottRatioIndicatorTest {

    @Test
    public void returnsNanWhenSwingsInsufficient() {
        var series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(10).highPrice(10).lowPrice(10).closePrice(10).volume(0).add();
        series.barBuilder().openPrice(11).highPrice(11).lowPrice(11).closePrice(11).volume(0).add();

        var swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var ratioIndicator = new ElliottRatioIndicator(swingIndicator);

        var ratio = ratioIndicator.getValue(series.getEndIndex());
        assertThat(ratio.type()).isEqualTo(RatioType.NONE);
        assertThat(ratio.value().isNaN()).isTrue();
    }

    @Test
    public void computesRetracementRatioFromSeries() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 9, 13, 8, 14, 7, 15, 6 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }
        var swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        var ratioIndicator = new ElliottRatioIndicator(swingIndicator);

        var swings = swingIndicator.getValue(series.getEndIndex());
        var latest = swings.get(swings.size() - 1);
        var previous = swings.get(swings.size() - 2);
        var expectedRatio = latest.amplitude().dividedBy(previous.amplitude());

        var ratio = ratioIndicator.getValue(series.getEndIndex());
        assertThat(ratio.type()).isEqualTo(RatioType.RETRACEMENT);
        assertThat(ratio.value()).isEqualByComparingTo(expectedRatio);

        var tolerance = series.numFactory().numOf(0.2);
        assertThat(ratioIndicator.isNearLevel(series.getEndIndex(), series.numFactory().numOf(1.0), tolerance))
                .isTrue();
    }

    @Test
    public void computesExtensionRatios() {
        var series = new MockBarSeriesBuilder().build();
        double[] closes = { 10, 12, 14, 16, 18 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(0).add();
        }
        var swings = new ArrayList<List<ElliottSwing>>();
        var factory = series.numFactory();
        swings.add(List.of());
        swings.add(List.of());
        swings.add(List.of(new ElliottSwing(0, 1, factory.numOf(10), factory.numOf(12), ElliottDegree.MINOR)));
        swings.add(List.of(new ElliottSwing(0, 1, factory.numOf(10), factory.numOf(12), ElliottDegree.MINOR),
                new ElliottSwing(1, 2, factory.numOf(12), factory.numOf(14), ElliottDegree.MINOR)));
        swings.add(List.of(new ElliottSwing(0, 1, factory.numOf(10), factory.numOf(12), ElliottDegree.MINOR),
                new ElliottSwing(1, 2, factory.numOf(12), factory.numOf(14), ElliottDegree.MINOR),
                new ElliottSwing(2, 3, factory.numOf(14), factory.numOf(16), ElliottDegree.MINOR)));
        swings.add(List.of(new ElliottSwing(0, 1, factory.numOf(10), factory.numOf(12), ElliottDegree.MINOR),
                new ElliottSwing(1, 2, factory.numOf(12), factory.numOf(14), ElliottDegree.MINOR),
                new ElliottSwing(2, 3, factory.numOf(14), factory.numOf(16), ElliottDegree.MINOR),
                new ElliottSwing(3, 4, factory.numOf(16), factory.numOf(18), ElliottDegree.MINOR)));

        var swingIndicator = new StubSwingIndicator(series, swings);
        var ratioIndicator = new ElliottRatioIndicator(swingIndicator);

        var ratio = ratioIndicator.getValue(series.getEndIndex());
        assertThat(ratio.type()).isEqualTo(RatioType.EXTENSION);
        var expected = factory.numOf(2).dividedBy(factory.numOf(2));
        assertThat(ratio.value()).isEqualByComparingTo(expected);
    }

    private static final class StubSwingIndicator extends ElliottSwingIndicator {

        private final List<List<ElliottSwing>> swingsByIndex;

        private StubSwingIndicator(final BarSeries series, final List<List<ElliottSwing>> swingsByIndex) {
            super(series, 1, ElliottDegree.MINOR);
            this.swingsByIndex = swingsByIndex;
        }

        @Override
        protected List<ElliottSwing> calculate(final int index) {
            if (index < swingsByIndex.size()) {
                return swingsByIndex.get(index);
            }
            return swingsByIndex.get(swingsByIndex.size() - 1);
        }
    }
}
