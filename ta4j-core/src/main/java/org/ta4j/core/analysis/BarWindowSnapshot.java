/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * The complete bar values an analysis captures from a live series window, used
 * to verify that the window still has the same values after user code runs
 * without the series lock.
 *
 * <p>
 * A revision check alone is not enough: custom series may report revision
 * {@code -1}, custom bars may not publish mutations, and a {@code BaseBar} may
 * change fields before its mutation is published. The value comparison covers
 * these cases; the revision also detects a value that changed and changed back.
 * </p>
 */
final class BarWindowSnapshot {

    private final int beginIndex;
    private final int endIndex;
    private final long revision;
    private final BarState[] barStates;

    private BarWindowSnapshot(int beginIndex, int endIndex, long revision, BarState[] barStates) {
        this.beginIndex = beginIndex;
        this.endIndex = endIndex;
        this.revision = revision;
        this.barStates = barStates;
    }

    /**
     * Captures the full state of {@code [beginIndex, endIndex]}. Must run inside
     * the series read scope.
     *
     * @param series     the analysed series, retaining the whole range
     * @param beginIndex first captured index
     * @param endIndex   last captured index; below {@code beginIndex} for an empty
     *                   snapshot
     * @return the snapshot
     */
    static BarWindowSnapshot capture(BarSeries series, int beginIndex, int endIndex) {
        int size = endIndex < beginIndex ? 0 : endIndex - beginIndex + 1;
        BarState[] barStates = new BarState[size];
        for (int offset = 0; offset < size; offset++) {
            barStates[offset] = BarState.capture(series.getBar(beginIndex + offset));
        }
        return new BarWindowSnapshot(beginIndex, endIndex, series.getBarHistoryRevision(), barStates);
    }

    private record BarState(Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume, Num amount,
            long trades, Duration timePeriod, Instant beginTime, Instant endTime) {

        private static BarState capture(Bar bar) {
            return new BarState(bar.getOpenPrice(), bar.getHighPrice(), bar.getLowPrice(), bar.getClosePrice(),
                    bar.getVolume(), bar.getAmount(), bar.getTrades(), bar.getTimePeriod(), bar.getBeginTime(),
                    bar.getEndTime());
        }

        private boolean matches(Bar bar) {
            return Objects.equals(openPrice, bar.getOpenPrice()) && Objects.equals(highPrice, bar.getHighPrice())
                    && Objects.equals(lowPrice, bar.getLowPrice()) && Objects.equals(closePrice, bar.getClosePrice())
                    && Objects.equals(volume, bar.getVolume()) && Objects.equals(amount, bar.getAmount())
                    && trades == bar.getTrades() && Objects.equals(timePeriod, bar.getTimePeriod())
                    && Objects.equals(beginTime, bar.getBeginTime()) && Objects.equals(endTime, bar.getEndTime());
        }
    }

    /**
     * @return whether {@code [firstIndex, lastIndex]} lies inside the snapshot
     */
    boolean covers(int firstIndex, int lastIndex) {
        return firstIndex > lastIndex || (firstIndex >= beginIndex && lastIndex <= endIndex);
    }

    /**
     * @param index an index inside the snapshot
     * @return the bar end time at {@code index}, or {@code null} outside the
     *         snapshot
     */
    Instant endTime(int index) {
        long offset = (long) index - beginIndex;
        return offset < 0 || offset >= barStates.length ? null : barStates[(int) offset].endTime();
    }

    /**
     * Checks that the series still retains the snapshot's bars with the captured
     * values. Bars appended after the snapshot are fine. Must run inside the series
     * read scope.
     *
     * @param series the series the snapshot was captured from
     * @return {@code true} if no captured bar was evicted, replaced or updated
     */
    boolean isUnchangedIn(BarSeries series) {
        if (barStates.length == 0) {
            return true;
        }
        if (series.getBeginIndex() > beginIndex || series.getEndIndex() < endIndex) {
            return false;
        }
        long currentRevision = series.getBarHistoryRevision();
        if (revision >= 0L && currentRevision >= 0L && currentRevision != revision) {
            int changedIndex = series.getBarSeriesChangeSnapshot(revision).earliestChangedIndex();
            if (changedIndex >= 0 && changedIndex <= endIndex) {
                return false;
            }
        }
        for (int offset = 0; offset < barStates.length; offset++) {
            if (!barStates[offset].matches(series.getBar(beginIndex + offset))) {
                return false;
            }
        }
        return true;
    }

    /** Returns the captured begin time, or null outside the window. */
    Instant beginTime(int index) {
        long offset = (long) index - beginIndex;
        return offset < 0 || offset >= barStates.length ? null : barStates[(int) offset].beginTime();
    }
}
