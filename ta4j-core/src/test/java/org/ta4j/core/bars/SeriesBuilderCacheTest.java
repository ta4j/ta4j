/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarBuilderFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

class SeriesBuilderCacheTest {

    @Test
    void sharedFactoryReusesBuilderPerSeries() {
        TickBarBuilderFactory factory = new TickBarBuilderFactory(3);
        BarSeries first = new BaseBarSeriesBuilder().withBarBuilderFactory(factory).build();
        BarSeries second = new BaseBarSeriesBuilder().withBarBuilderFactory(factory).build();

        assertThat(first.barBuilder()).isSameAs(first.barBuilder());
        assertThat(first.barBuilder()).isNotSameAs(second.barBuilder());
        assertThat(factory.builders().size()).isEqualTo(2);
    }

    @Test
    void sharedFactoryBindsBuildersWeakly() {
        TickBarBuilderFactory tick = new TickBarBuilderFactory(3);
        VolumeBarBuilderFactory volume = new VolumeBarBuilderFactory(3);
        AmountBarBuilderFactory amount = new AmountBarBuilderFactory(3, true);

        TickBarBuilder tickBuilder = (TickBarBuilder) newSeries(tick).barBuilder();
        VolumeBarBuilder volumeBuilder = (VolumeBarBuilder) newSeries(volume).barBuilder();
        AmountBarBuilder amountBuilder = (AmountBarBuilder) newSeries(amount).barBuilder();

        assertThat(tickBuilder.boundBarAppender()).isInstanceOf(SeriesBuilderCache.WeakAppender.class);
        assertThat(volumeBuilder.boundBarAppender()).isInstanceOf(SeriesBuilderCache.WeakAppender.class);
        assertThat(amountBuilder.boundBarAppender()).isInstanceOf(SeriesBuilderCache.WeakAppender.class);
    }

    @Test
    void weakAppenderDropsBarsOnceSeriesIsCleared() {
        BarSeries series = new BaseBarSeriesBuilder().build();
        SeriesBuilderCache.WeakAppender appender = SeriesBuilderCache.weakAppender(series);
        Instant end = Instant.parse("2026-01-01T00:01:00Z");

        appender.accept(bar(series, end));
        assertThat(series.getBarCount()).isEqualTo(1);

        appender.clear();
        appender.accept(bar(series, end.plus(Duration.ofMinutes(1))));
        assertThat(series.getBarCount()).isEqualTo(1);
    }

    @Test
    void clearedSeriesKeysAreEvicted() {
        TickBarBuilderFactory tick = new TickBarBuilderFactory(3);
        BarSeries kept = newSeries(tick);
        BarSeries discarded = newSeries(tick);
        BarBuilder keptBuilder = kept.barBuilder();
        discarded.barBuilder();
        assertThat(tick.builders().size()).isEqualTo(2);

        tick.builders().clearKey(discarded);

        assertThat(tick.builders().size()).isEqualTo(1);
        assertThat(kept.barBuilder()).isSameAs(keptBuilder);
    }

    private static BarSeries newSeries(BarBuilderFactory factory) {
        return new BaseBarSeriesBuilder().withBarBuilderFactory(factory).build();
    }

    private static Bar bar(BarSeries series, Instant end) {
        return new TimeBarBuilder(series.numFactory()).timePeriod(Duration.ofMinutes(1))
                .endTime(end)
                .openPrice(1)
                .highPrice(1)
                .lowPrice(1)
                .closePrice(1)
                .volume(1)
                .build();
    }
}
