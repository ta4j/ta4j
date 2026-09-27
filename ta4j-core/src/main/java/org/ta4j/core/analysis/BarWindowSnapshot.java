/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.time.Instant;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * The bar values an analysis reads from a window of a live series, captured so
 * the analysis can verify, after running user code without the series lock,
 * that the window still holds the same values.
 *
 * <p>
 * The revision check alone is not enough: series that report revision
 * {@code -1}, custom {@code Bar} classes and a {@code BaseBar} whose fields
 * change before its mutation is published all alter values without moving the
 * revision. The value comparison covers those; the revision covers a value that
 * changed and changed back.
 * </p>
 */
final class BarWindowSnapshot {

    private final int beginIndex;
    private final int endIndex;
    private final long revision;
    private final Num[] closePrices;
    private final Instant[] endTimes;

    private BarWindowSnapshot(int beginIndex, int endIndex, long revision, Num[] closePrices, Instant[] endTimes) {
        this.beginIndex = beginIndex;
        this.endIndex = endIndex;
        this.revision = revision;
        this.closePrices = closePrices;
        this.endTimes = endTimes;
    }

    /**
     * Captures the close prices, and optionally end times, of
     * {@code [beginIndex, endIndex]}. Must run inside the series read scope.
     *
     * @param series         the analysed series, retaining the whole range
     * @param beginIndex     first captured index
     * @param endIndex       last captured index; below {@code beginIndex} for an
     *                       empty snapshot
     * @param captureEndTime whether bar end times are captured as well
     * @return the snapshot
     */
    static BarWindowSnapshot capture(BarSeries series, int beginIndex, int endIndex, boolean captureEndTime) {
        int size = endIndex < beginIndex ? 0 : endIndex - beginIndex + 1;
        Num[] closePrices = new Num[size];
        Instant[] endTimes = captureEndTime ? new Instant[size] : null;
        for (int offset = 0; offset < size; offset++) {
            var bar = series.getBar(beginIndex + offset);
            closePrices[offset] = bar.getClosePrice();
            if (endTimes != null) {
                endTimes[offset] = bar.getEndTime();
            }
        }
        return new BarWindowSnapshot(beginIndex, endIndex, series.getBarHistoryRevision(), closePrices, endTimes);
    }

    /**
     * @return whether {@code [firstIndex, lastIndex]} lies inside the snapshot
     */
    boolean covers(int firstIndex, int lastIndex) {
        return firstIndex > lastIndex || (firstIndex >= beginIndex && lastIndex <= endIndex);
    }

    /**
     * @param index an index inside the snapshot captured with end times
     * @return the bar end time at {@code index}, or {@code null} outside the
     *         snapshot
     */
    Instant endTime(int index) {
        long offset = (long) index - beginIndex;
        return offset < 0 || offset >= endTimes.length ? null : endTimes[(int) offset];
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
        if (closePrices.length == 0) {
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
        for (int offset = 0; offset < closePrices.length; offset++) {
            var bar = series.getBar(beginIndex + offset);
            if (!Objects.equals(closePrices[offset], bar.getClosePrice())
                    || (endTimes != null && !Objects.equals(endTimes[offset], bar.getEndTime()))) {
                return false;
            }
        }
        return true;
    }
}
