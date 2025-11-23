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
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.Arrays;
import java.util.List;

public class AbstractRecentSwingIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public AbstractRecentSwingIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldExposeSwingIndexesAndValuesMonotonically() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        assertThat(indicator.getSwingPointIndexesUpTo(2)).containsExactly(2);
        assertThat(indicator.getSwingPointIndexesUpTo(5)).containsExactly(2, 5);
        // Once discovered, swing points remain visible even when requesting an earlier
        // index
        assertThat(indicator.getSwingPointIndexesUpTo(3)).containsExactly(2, 5);

        assertThat(indicator.getLatestSwingIndex(4)).isEqualTo(2);
        assertThat(indicator.getLatestSwingIndex(6)).isEqualTo(5);
        assertThat(indicator.getValue(6)).isEqualByComparingTo(numOf(6));
    }

    @Test
    public void shouldPurgeSwingIndexesThatFallBeforeSeriesBegin() {
        final var series = seriesFromCloses(1, 2, 3, 4, 5, 6, 7);
        final int[] latestSwingIndexes = { -1, -1, 2, 2, 2, 5, 5 };
        final var indicator = new FixedSwingIndicator(new ClosePriceIndicator(series), latestSwingIndexes);

        assertThat(indicator.getSwingPointIndexesUpTo(series.getEndIndex())).containsExactly(2, 5);

        series.setMaximumBarCount(2); // beginIndex will advance to drop the swing at index 2
        final int endIndexAfterPurge = series.getEndIndex();
        assertThat(indicator.getSwingPointIndexesUpTo(endIndexAfterPurge)).containsExactly(5);
        assertThat(indicator.getLatestSwingIndex(endIndexAfterPurge)).isEqualTo(5);
        assertThat(indicator.getValue(endIndexAfterPurge)).isNotEqualTo(NaN);
    }

    private BarSeries seriesFromCloses(double... closes) {
        final var seriesBuilder = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (double close : closes) {
            seriesBuilder.barBuilder().openPrice(close).closePrice(close).highPrice(close).lowPrice(close).add();
        }
        return seriesBuilder;
    }

    private static final class FixedSwingIndicator extends AbstractRecentSwingIndicator {

        private final List<Integer> latestSwingIndexes;

        private FixedSwingIndicator(Indicator<Num> priceIndicator, int[] latestSwingIndexes) {
            super(priceIndicator, 0);
            this.latestSwingIndexes = Arrays.stream(latestSwingIndexes).boxed().toList();
        }

        @Override
        protected int detectLatestSwingIndex(int index) {
            if (index < 0) {
                return -1;
            }
            if (index >= latestSwingIndexes.size()) {
                return latestSwingIndexes.get(latestSwingIndexes.size() - 1);
            }
            return latestSwingIndexes.get(index);
        }
    }
}
