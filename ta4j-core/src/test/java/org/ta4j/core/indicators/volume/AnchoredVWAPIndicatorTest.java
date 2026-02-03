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
package org.ta4j.core.indicators.volume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AnchoredVWAPIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public AnchoredVWAPIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(10).closePrice(10).highPrice(10).lowPrice(10).volume(100).add();
        series.barBuilder().openPrice(11).closePrice(11).highPrice(11).lowPrice(11).volume(200).add();
        series.barBuilder().openPrice(12).closePrice(12).highPrice(12).lowPrice(12).volume(150).add();
        series.barBuilder().openPrice(13).closePrice(13).highPrice(13).lowPrice(13).volume(300).add();
        series.barBuilder().openPrice(14).closePrice(14).highPrice(14).lowPrice(14).volume(250).add();
    }

    @Test
    public void anchoredFromFixedIndex() {
        var anchored = new AnchoredVWAPIndicator(series, 2);

        assertThat(anchored.getAnchorIndex(4)).isEqualTo(2);
        assertThat(anchored.getWindowStartIndex(4)).isEqualTo(2);
        assertNumEquals((12 * 150 + 13 * 300 + 14 * 250) / 700d, anchored.getValue(4));
    }

    @Test
    public void anchorResetsOnSignal() {
        var signal = new AnchorSignal(series, 0, 3);
        var anchored = new AnchoredVWAPIndicator(series, signal);

        assertThat(anchored.getAnchorIndex(2)).isEqualTo(0);
        assertNumEquals((10 * 100 + 11 * 200 + 12 * 150) / 450d, anchored.getValue(2));

        assertThat(anchored.getAnchorIndex(3)).isEqualTo(3);
        assertNumEquals(13d, anchored.getValue(3));

        assertThat(anchored.getAnchorIndex(4)).isEqualTo(3);
        assertNumEquals((13 * 300 + 14 * 250) / 550d, anchored.getValue(4));
    }

    @Test
    public void clampsAnchorBeforeSeriesStart() {
        var anchored = new AnchoredVWAPIndicator(series, -5);

        assertThat(anchored.getAnchorIndex(0)).isEqualTo(series.getBeginIndex());
        assertNumEquals(10d, anchored.getValue(0));
    }

    @Test
    public void rejectsAnchorSignalFromDifferentSeries() {
        var otherSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        otherSeries.barBuilder().openPrice(1).closePrice(1).highPrice(1).lowPrice(1).volume(1).add();
        var alienSignal = new AnchorSignal(otherSeries, 0);

        assertThatThrownBy(() -> new AnchoredVWAPIndicator(series, alienSignal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bar series");
    }

    private static final class AnchorSignal implements Indicator<Boolean> {

        private final BarSeries series;
        private final Set<Integer> anchors;

        private AnchorSignal(BarSeries series, int... anchorIndexes) {
            this.series = series;
            this.anchors = new HashSet<>();
            for (int index : anchorIndexes) {
                anchors.add(index);
            }
        }

        @Override
        public Boolean getValue(int index) {
            return anchors.contains(index);
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }
}
