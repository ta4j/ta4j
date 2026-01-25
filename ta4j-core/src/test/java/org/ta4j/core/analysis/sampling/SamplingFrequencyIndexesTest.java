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
package org.ta4j.core.analysis.sampling;

import java.util.stream.Stream;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.ta4j.core.analysis.frequency.IndexPair;
import org.ta4j.core.analysis.frequency.SamplingFrequencyIndexes;
import org.ta4j.core.analysis.frequency.SamplingFrequency;

public class SamplingFrequencyIndexesTest {

    @Test
    public void samplePerBarUsesConsecutivePairs() {
        var series = buildDailySeries();
        var sampler = new SamplingFrequencyIndexes(SamplingFrequency.BAR, ZoneOffset.UTC);

        var pairs = sampler.sample(series, 0, 1, 3).toList();

        var expected = List.of(new IndexPair(0, 1), new IndexPair(1, 2), new IndexPair(2, 3));

        assertEquals(expected, pairs);
    }

    @Test
    public void sampleDailyUsesPeriodEnds() {
        var series = buildDailySeries();
        var sampler = new SamplingFrequencyIndexes(SamplingFrequency.DAY, ZoneOffset.UTC);

        var pairs = sampler.sample(series, 0, 1, 3).toList();

        var expected = Stream.of(new IndexPair(0, 1), new IndexPair(1, 2), new IndexPair(2, 3)).toList();

        assertEquals(expected, pairs);
    }

    @Test
    public void sampleDailyAnchorsAtExplicitIndex() {
        var series = buildIntradaySeries();
        var sampler = new SamplingFrequencyIndexes(SamplingFrequency.DAY, ZoneOffset.UTC);

        var pairs = sampler.sample(series, 0, 1, 3).toList();

        var expected = List.of(new IndexPair(0, 1), new IndexPair(1, 3));

        assertEquals(expected, pairs);
    }

    @Test
    public void sampleMonthlyUsesMonthBoundary() {
        var series = buildMonthlyBoundarySeries();
        var sampler = new SamplingFrequencyIndexes(SamplingFrequency.MONTH, ZoneOffset.UTC);

        var pairs = sampler.sample(series, 0, 1, 2).toList();

        var expected = List.of(new IndexPair(0, 1), new IndexPair(1, 2));

        assertEquals(expected, pairs);
    }

    @Test
    public void sampleReturnsEmptyWhenRangeHasSingleIndex() {
        var series = buildDailySeries();
        var sampler = new SamplingFrequencyIndexes(SamplingFrequency.BAR, ZoneOffset.UTC);

        var pairs = sampler.sample(series, 0, 2, 2).toList();

        assertEquals(List.of(), pairs);
    }

    @Test
    public void sampleReturnsEmptyWhenRangeIsReversed() {
        var series = buildDailySeries();
        var sampler = new SamplingFrequencyIndexes(SamplingFrequency.DAY, ZoneOffset.UTC);

        var pairs = sampler.sample(series, 0, 3, 1).toList();

        assertEquals(List.of(), pairs);
    }

    private static BarSeries buildDailySeries() {
        var series = new BaseBarSeriesBuilder().withName("sampler_series").build();
        var start = Instant.parse("2024-01-01T00:00:00Z");
        var closes = new double[] { 100d, 110d, 120d, 130d };

        for (var i = 0; i < closes.length; i++) {
            var endTime = start.plus(Duration.ofDays(i + 1L));
            var close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(endTime)
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        }

        return series;
    }

    private static BarSeries buildIntradaySeries() {
        var series = new BaseBarSeriesBuilder().withName("intraday_sampler_series").build();
        var start = Instant.parse("2024-01-01T00:00:00Z");
        var closes = new double[] { 100d, 105d, 110d, 120d };
        var offsets = new long[] { 6, 20, 30, 44 };

        for (var i = 0; i < closes.length; i++) {
            var endTime = start.plus(Duration.ofHours(offsets[i]));
            var close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofHours(1))
                    .endTime(endTime)
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        }

        return series;
    }

    private static BarSeries buildMonthlyBoundarySeries() {
        var series = new BaseBarSeriesBuilder().withName("monthly_sampler_series").build();
        var endTimes = new Instant[] { Instant.parse("2024-01-30T00:00:00Z"), Instant.parse("2024-01-31T00:00:00Z"),
                Instant.parse("2024-02-01T00:00:00Z") };
        var closes = new double[] { 100d, 103d, 104d };

        for (var i = 0; i < closes.length; i++) {
            var close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(endTimes[i])
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        }

        return series;
    }
}
