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
        // Copy the bars once and reconcile the snapshot with any concurrent
        // mutation instead of retrying: an unbounded copy-retry could starve
        // when a moving series updates at least once per pass (an at-capacity
        // series prunes on every append, so the removed-bars count never stays
        // still). Appends during the copy only yield a slightly stale but
        // coherent window; expired-bar removals shift logical indexes, so the
        // copied prefix is trimmed by the removal delta and bounds follow the
        // retained window.
        int removedBarsCount = barSeries.getRemovedBarsCount();
        List<Bar> sourceBars = barSeries.getBarData();
        List<Bar> copiedBars = new ArrayList<>(sourceBars.size());
        for (Bar bar : sourceBars) {
            copiedBars.add(copyBar(bar, barSeries.numFactory()));
        }
        int removedAfterCopy = barSeries.getRemovedBarsCount();
        int prunedDuringCopy = Math.max(0, removedAfterCopy - removedBarsCount);
        List<Bar> retainedBars = prunedDuringCopy >= copiedBars.size() ? List.of()
                : copiedBars.subList(prunedDuringCopy, copiedBars.size());
        return new BaseBarSeriesBuilder().withName(barSeries.getName())
                .withNumFactory(barSeries.numFactory())
                .withMaxBarCount(barSeries.getMaximumBarCount())
                .withBeginIndex(Math.max(0, removedAfterCopy))
                .withBars(retainedBars)
                .build();
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
