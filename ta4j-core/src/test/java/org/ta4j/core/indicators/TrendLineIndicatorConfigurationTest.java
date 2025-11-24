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

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.ToleranceSettings;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.ToleranceSettings.Mode;
import org.ta4j.core.indicators.supportresistance.TrendLineSupportIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class TrendLineIndicatorConfigurationTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public TrendLineIndicatorConfigurationTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void shouldExposeDefaultCapsAndToleranceInDescriptor() {
        final var series = seriesFromLows(9, 7, 10, 11, 12, 6, 9, 13, 8);
        final var indicator = new TrendLineSupportIndicator(series, 1, 15);

        indicator.getValue(series.getEndIndex());

        final ToleranceSettings tolerance = indicator.getToleranceSettings();
        assertThat(tolerance.mode).isEqualTo(Mode.PERCENTAGE);
        assertThat(tolerance.value).isEqualTo(0.02d);
        assertThat(tolerance.minimumAbsolute).isEqualTo(1e-9d);
        assertThat(indicator.getMaxSwingPointsForTrendline())
                .isEqualTo(AbstractTrendLineIndicator.DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE);
        assertThat(indicator.getMaxCandidatePairs()).isEqualTo(AbstractTrendLineIndicator.DEFAULT_MAX_CANDIDATE_PAIRS);

        final ComponentDescriptor descriptor = indicator.toDescriptor();
        assertThat(descriptor.getParameters()).containsEntry("maxSwingPointsForTrendline",
                AbstractTrendLineIndicator.DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE);
        assertThat(descriptor.getParameters()).containsEntry("maxCandidatePairs",
                AbstractTrendLineIndicator.DEFAULT_MAX_CANDIDATE_PAIRS);

        try {
            assertThat(Integer.parseInt(descriptor.getParameters().get("toleranceMode").toString()))
                    .isEqualTo(tolerance.mode.ordinal());
            assertThat(Double.parseDouble(descriptor.getParameters().get("toleranceValue").toString()))
                    .isEqualTo(tolerance.value);
            assertThat(Double.parseDouble(descriptor.getParameters().get("toleranceMinimum").toString()))
                    .isEqualTo(tolerance.minimumAbsolute);
        } catch (NumberFormatException e) {
            fail("Could not parse tolerance value", e);
        }
    }

    @Test
    public void shouldReturnSegmentMetadata() {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        final double[] lows = { 10d, 15d, 12d };
        for (double low : lows) {
            series.barBuilder().openPrice(low).closePrice(low).highPrice(low + 1d).lowPrice(low).add();
        }
        final var swingIndicator = new StaticSwingIndicator(new LowPriceIndicator(series), List.of(0, 1, 2));
        final var indicator = new TrendLineSupportIndicator(swingIndicator, 10, 0, 0.30d, 0.20d, 0.15d, 0.20d, 0.15d);

        indicator.getValue(series.getEndIndex());
        final AbstractTrendLineIndicator.TrendLineSegment segment = indicator.getCurrentSegment();

        assertThat(segment).isNotNull();
        assertThat(segment.firstIndex).isEqualTo(0);
        assertThat(segment.secondIndex).isEqualTo(2);
        assertThat(segment.touchCount).isEqualTo(2);
        assertThat(segment.outsideCount).isEqualTo(1);
        assertThat(segment.touchesExtreme).isTrue();
        assertThat(segment.windowStart).isEqualTo(series.getBeginIndex());
        assertThat(segment.windowEnd).isEqualTo(series.getEndIndex());
    }

    @Test
    public void shouldClampNegativeToleranceMinimum() {
        final ToleranceSettings tolerance = ToleranceSettings.from(Mode.ABSOLUTE, 1.5d, -10d);
        assertThat(tolerance.minimumAbsolute).isZero();
    }

    private static final class StaticSwingIndicator extends CachedIndicator<Num> implements RecentSwingIndicator {

        private final List<Integer> swingIndexes;
        private final Indicator<Num> priceIndicator;

        private StaticSwingIndicator(Indicator<Num> priceIndicator, List<Integer> swingIndexes) {
            super(priceIndicator);
            this.priceIndicator = priceIndicator;
            this.swingIndexes = new ArrayList<>(swingIndexes);
        }

        @Override
        protected Num calculate(int index) {
            final int latest = getLatestSwingIndex(index);
            if (latest < 0) {
                return getBarSeries().numFactory().numOf(0);
            }
            return priceIndicator.getValue(latest);
        }

        @Override
        public int getLatestSwingIndex(int index) {
            int latest = -1;
            for (int swingIndex : swingIndexes) {
                if (swingIndex <= index) {
                    latest = swingIndex;
                } else {
                    break;
                }
            }
            return latest;
        }

        @Override
        public List<Integer> getSwingPointIndexesUpTo(int index) {
            final List<Integer> result = new ArrayList<>();
            for (int swingIndex : swingIndexes) {
                if (swingIndex <= index) {
                    result.add(swingIndex);
                }
            }
            return result;
        }

        @Override
        public Indicator<Num> getPriceIndicator() {
            return priceIndicator;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private BarSeries seriesFromLows(double... lows) {
        final var builder = new MockBarSeriesBuilder().withNumFactory(numFactory);
        final var series = builder.build();
        for (double low : lows) {
            final double high = low + 1d;
            series.barBuilder().openPrice(low).closePrice(low).highPrice(high).lowPrice(low).add();
        }
        return series;
    }
}
