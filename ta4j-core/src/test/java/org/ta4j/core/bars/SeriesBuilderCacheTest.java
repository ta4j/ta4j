/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ref.WeakReference;

import org.junit.jupiter.api.Test;
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
    void sharedFactoryDoesNotRetainDiscardedSeries() {
        TickBarBuilderFactory tick = new TickBarBuilderFactory(3);
        VolumeBarBuilderFactory volume = new VolumeBarBuilderFactory(3);
        AmountBarBuilderFactory amount = new AmountBarBuilderFactory(3, true);
        WeakReference<BarSeries> lastSeries = null;
        for (int i = 0; i < 50; i++) {
            BarSeries series = new BaseBarSeriesBuilder().withBarBuilderFactory(tick).build();
            series.barBuilder();
            new BaseBarSeriesBuilder().withBarBuilderFactory(volume).build().barBuilder();
            new BaseBarSeriesBuilder().withBarBuilderFactory(amount).build().barBuilder();
            lastSeries = new WeakReference<>(series);
        }

        for (int attempt = 0; attempt < 50 && (lastSeries.get() != null || tick.builders().size() > 0
                || volume.builders().size() > 0 || amount.builders().size() > 0); attempt++) {
            System.gc();
            Thread.onSpinWait();
        }

        assertThat(lastSeries.get()).isNull();
        assertThat(tick.builders().size()).isZero();
        assertThat(volume.builders().size()).isZero();
        assertThat(amount.builders().size()).isZero();
    }
}
