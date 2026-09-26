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
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Internal helper that captures series state for the equity analysis curves.
 * Not part of the public API.
 *
 * @since 0.25.1
 */
final class SeriesSnapshots {

    private SeriesSnapshots() {
    }

    /**
     * Captures the given series' retained bars and close prices. The capture holds
     * the close prices eagerly, so calculations never observe later in-place bar
     * edits, and only references to the retained bars, so capturing neither copies
     * bar objects nor registers the bars with another series. The series keeps its
     * absolute indexing: after pruning, retained bars keep their original indices.
     *
     * @param barSeries the series to capture, not null
     * @return the capture
     */
    static CapturedSeries capture(BarSeries barSeries) {
        Objects.requireNonNull(barSeries);
        // Concurrent series mutate under a write lock, so reading the bar list
        // and its bounds inside the read lock makes the capture atomic. Plain
        // series are documented as single-threaded, so their reads are coherent
        // by contract; the removal delta below still keeps indices aligned if a
        // moving series prunes while the bar list is read.
        if (barSeries instanceof ConcurrentBarSeries concurrentBarSeries) {
            return concurrentBarSeries.withReadLock(() -> read(barSeries));
        }
        return read(barSeries);
    }

    private static CapturedSeries read(BarSeries barSeries) {
        // Read the bar list before the counters: any prune already reflected in
        // this list is also reflected in the baseline read right after it, so
        // the reconciliation below never trims a retained bar twice.
        List<Bar> bars = List.copyOf(barSeries.getBarData());
        int beginIndex = barSeries.getBeginIndex();
        int removedBarsAtCapture = barSeries.getRemovedBarsCount();
        int prunedDuringCapture = Math.max(0, barSeries.getRemovedBarsCount() - removedBarsAtCapture);
        if (prunedDuringCapture > 0) {
            bars = prunedDuringCapture >= bars.size() ? List.of() : bars.subList(prunedDuringCapture, bars.size());
            beginIndex += prunedDuringCapture;
        }
        int endIndex = bars.isEmpty() ? barSeries.getEndIndex() : Math.max(0, beginIndex) + bars.size() - 1;
        return new CapturedSeries(barSeries.getName(), barSeries.numFactory(), barSeries.getMaximumBarCount(),
                bars.isEmpty() ? beginIndex : Math.max(0, beginIndex), endIndex, bars);
    }

    /**
     * Immutable capture of a series' retained window: its absolute bounds, number
     * factory, close prices, and bar references for building a detached series on
     * demand.
     */
    static final class CapturedSeries {

        private final String name;
        private final NumFactory numFactory;
        private final int maximumBarCount;
        private final int beginIndex;
        private final int endIndex;
        private final List<Bar> bars;
        private final Num[] closePrices;

        private CapturedSeries(String name, NumFactory numFactory, int maximumBarCount, int beginIndex, int endIndex,
                List<Bar> bars) {
            this.name = name;
            this.numFactory = numFactory;
            this.maximumBarCount = maximumBarCount;
            this.beginIndex = beginIndex;
            this.endIndex = endIndex;
            this.bars = bars;
            this.closePrices = new Num[bars.size()];
            for (int i = 0; i < closePrices.length; i++) {
                closePrices[i] = bars.get(i).getClosePrice();
            }
        }

        int beginIndex() {
            return beginIndex;
        }

        int endIndex() {
            return endIndex;
        }

        int barCount() {
            return closePrices.length;
        }

        NumFactory numFactory() {
            return numFactory;
        }

        /**
         * @param index an absolute index within {@code [beginIndex, endIndex]}
         * @return the close price captured for that bar
         */
        Num closePrice(int index) {
            return closePrices[index - beginIndex];
        }

        /**
         * Mirrors {@link PerformanceIndicator#resolveExitPrice}: the exit's net price
         * when the position exited by {@code endIndex}, otherwise the captured close
         * price at {@code endIndex}.
         */
        Num exitPrice(Position position, int endIndex) {
            Trade exit = position.getExit();
            if (exit != null && exit.getIndex() <= endIndex) {
                return exit.getNetPrice();
            }
            return closePrice(endIndex);
        }

        /**
         * Builds a detached series holding deep copies of the captured bars, so
         * mutating it cannot reach the source series. Specialized bar types such as
         * {@link BaseRealtimeBar} keep their side and liquidity metadata.
         *
         * @return a new detached series with the captured bars and absolute indices
         */
        BarSeries toDetachedSeries() {
            BaseBarSeriesBuilder builder = new BaseBarSeriesBuilder().withName(name)
                    .withNumFactory(numFactory)
                    .withMaxBarCount(maximumBarCount)
                    .withBeginIndex(Math.max(0, beginIndex));
            if (bars.isEmpty()) {
                return builder.build();
            }
            List<Bar> copiedBars = new ArrayList<>(bars.size());
            for (Bar bar : bars) {
                copiedBars.add(copyBar(bar, numFactory));
            }
            return builder.withBars(copiedBars).build();
        }
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
