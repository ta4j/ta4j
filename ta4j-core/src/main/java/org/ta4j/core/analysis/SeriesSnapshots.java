/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseRealtimeBar;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Internal helper that creates detached, deep-copied series snapshots for the
 * equity analysis indicators. Not part of the public API.
 *
 * @since 0.24.2
 */
final class SeriesSnapshots {

    private SeriesSnapshots() {
    }

    /**
     * Creates a series mirroring the given one, but owning deep copies of its bar
     * data so later in-place edits of the original bars cannot reach values
     * computed from the copy. Specialized bar types such as {@link BaseRealtimeBar}
     * are recreated with their side and liquidity metadata preserved instead of
     * being normalized to plain {@link BaseBar} instances. The snapshot keeps the
     * source's absolute indexing: when the source has already pruned bars, its
     * retained bars keep their original indices instead of being renumbered from
     * zero.
     *
     * @param barSeries the series to copy, not null
     * @return the detached deep-copy snapshot
     */
    static BarSeries deepCopy(BarSeries barSeries) {
        Objects.requireNonNull(barSeries);
        // Concurrent series mutate under a write lock, so capturing the bar
        // list and its bounds inside the read lock makes the copy atomic; an
        // unbounded copy-retry would instead starve when an at-capacity moving
        // series prunes on every append. Plain series are documented as
        // single-threaded, so their reads are coherent by contract.
        if (barSeries instanceof ConcurrentBarSeries concurrentBarSeries) {
            return concurrentBarSeries.withReadLock(() -> snapshot(barSeries));
        }
        return snapshot(barSeries);
    }

    private static BarSeries snapshot(BarSeries barSeries) {
        // Capture the bar list before the counters: any prune already reflected
        // in this list is also reflected in the baseline read right after it,
        // so the reconciliation below never trims a retained bar twice.
        List<Bar> sourceBars = barSeries.getBarData();
        int beginIndexAtCapture = Math.max(0, barSeries.getBeginIndex());
        int removedBarsAtCapture = barSeries.getRemovedBarsCount();
        List<Bar> copiedBars = new ArrayList<>(sourceBars.size());
        for (Bar bar : sourceBars) {
            copiedBars.add(copyBar(bar, barSeries.numFactory()));
        }
        // Appends during the copy only yield a slightly stale but coherent
        // window; expired-bar removals shift logical indexes, so the copied
        // prefix is trimmed by the removal delta and the first retained bar
        // keeps its source index.
        int prunedDuringCopy = Math.max(0, barSeries.getRemovedBarsCount() - removedBarsAtCapture);
        BaseBarSeriesBuilder builder = new BaseBarSeriesBuilder().withName(barSeries.getName())
                .withNumFactory(barSeries.numFactory())
                .withMaxBarCount(barSeries.getMaximumBarCount());
        if (copiedBars.isEmpty()) {
            return builder.withBeginIndex(beginIndexAtCapture).build();
        }
        int retainedBeginIndex = beginIndexAtCapture + prunedDuringCopy;
        List<Bar> retainedBars = prunedDuringCopy >= copiedBars.size() ? List.of()
                : copiedBars.subList(prunedDuringCopy, copiedBars.size());
        return builder.withBeginIndex(retainedBeginIndex).withBars(retainedBars).build();
    }

    private static Bar copyBar(Bar bar, NumFactory numFactory) {
        if (bar instanceof BaseRealtimeBar realtimeBar) {
            return new BaseRealtimeBar(bar.getTimePeriod(), bar.getBeginTime(), bar.getEndTime(), bar.getOpenPrice(),
                    bar.getHighPrice(), bar.getLowPrice(), bar.getClosePrice(), bar.getVolume(), bar.getAmount(),
                    bar.getTrades(), realtimeBar.getBuyVolume(), realtimeBar.getSellVolume(),
                    realtimeBar.getBuyAmount(), realtimeBar.getSellAmount(), realtimeBar.getBuyTrades(),
                    realtimeBar.getSellTrades(), realtimeBar.getMakerVolume(), realtimeBar.getTakerVolume(),
                    realtimeBar.getMakerAmount(), realtimeBar.getTakerAmount(), realtimeBar.getMakerTrades(),
                    realtimeBar.getTakerTrades(), realtimeBar.hasSideData(), realtimeBar.hasLiquidityData(),
                    numFactory);
        }
        return new BaseBar(bar.getTimePeriod(), bar.getBeginTime(), bar.getEndTime(), bar.getOpenPrice(),
                bar.getHighPrice(), bar.getLowPrice(), bar.getClosePrice(), bar.getVolume(), bar.getAmount(),
                bar.getTrades());
    }

}
