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
package org.ta4j.core.indicators.wyckoff;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecentSwingHighIndicator;
import org.ta4j.core.indicators.RecentSwingLowIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Tracks trading-range structure using recent swing highs and lows.
 *
 * @since 0.19
 */
public final class WyckoffStructureTracker {

    private final BarSeries series;
    private final RecentSwingHighIndicator swingHighIndicator;
    private final RecentSwingLowIndicator swingLowIndicator;
    private final HighPriceIndicator highPriceIndicator;
    private final LowPriceIndicator lowPriceIndicator;
    private final ClosePriceIndicator closePriceIndicator;
    private final Num breakoutTolerance;
    private final transient Map<Integer, StructureSnapshot> snapshotCache;

    /**
     * Creates a tracker that extracts trading range information from swing points.
     *
     * @param series             the bar series to analyse
     * @param precedingSwingBars required lower bars preceding a swing
     * @param followingSwingBars required lower bars following a swing
     * @param allowedEqualBars   number of equal bars tolerated when confirming a
     *                           swing
     * @param breakoutTolerance  tolerance applied when classifying breakouts
     *                           relative to the range bounds
     * @since 0.19
     */
    public WyckoffStructureTracker(BarSeries series, int precedingSwingBars, int followingSwingBars,
            int allowedEqualBars, Num breakoutTolerance) {
        this.series = Objects.requireNonNull(series, "series");
        this.swingHighIndicator = new RecentSwingHighIndicator(new HighPriceIndicator(series), precedingSwingBars,
                followingSwingBars, allowedEqualBars);
        this.swingLowIndicator = new RecentSwingLowIndicator(new LowPriceIndicator(series), precedingSwingBars,
                followingSwingBars, allowedEqualBars);
        this.highPriceIndicator = new HighPriceIndicator(series);
        this.lowPriceIndicator = new LowPriceIndicator(series);
        this.closePriceIndicator = new ClosePriceIndicator(series);
        this.breakoutTolerance = Objects.requireNonNull(breakoutTolerance, "breakoutTolerance");
        if (isInvalid(this.breakoutTolerance)) {
            throw new IllegalArgumentException("breakoutTolerance must be a valid number");
        }
        this.snapshotCache = new ConcurrentHashMap<>();
    }

    /**
     * Returns the current structure snapshot.
     *
     * @param index the bar index to inspect
     * @return immutable structure snapshot
     * @since 0.19
     */
    public StructureSnapshot snapshot(int index) {
        if (index < series.getBeginIndex() || index > series.getEndIndex()) {
            return StructureSnapshot.empty();
        }
        final StructureSnapshot cached = snapshotCache.get(index);
        if (cached != null) {
            return cached;
        }
        final int beginIndex = series.getBeginIndex();
        int computeFrom = index;
        while (computeFrom > beginIndex && !snapshotCache.containsKey(computeFrom - 1)) {
            computeFrom--;
        }
        StructureSnapshot previous = computeFrom > beginIndex ? snapshotCache.get(computeFrom - 1)
                : StructureSnapshot.empty();
        for (int i = Math.max(beginIndex, computeFrom); i <= index; i++) {
            previous = computeSnapshot(i, previous);
            snapshotCache.put(i, previous);
        }
        return snapshotCache.get(index);
    }

    private StructureSnapshot computeSnapshot(int index, StructureSnapshot previous) {
        final Num close = closePriceIndicator.getValue(index);
        if (isInvalid(close)) {
            return StructureSnapshot.empty();
        }
        final int latestHighIndex = swingHighIndicator.getLatestSwingIndex(index);
        final int latestLowIndex = swingLowIndicator.getLatestSwingIndex(index);
        Num rangeHigh = latestHighIndex >= 0 ? highPriceIndicator.getValue(latestHighIndex) : NaN;
        Num rangeLow = latestLowIndex >= 0 ? lowPriceIndicator.getValue(latestLowIndex) : NaN;
        int rangeHighIndex = latestHighIndex;
        int rangeLowIndex = latestLowIndex;

        if (previous != null && !isInvalid(previous.rangeHigh())) {
            if (isInvalid(rangeHigh) || previous.rangeHigh().isGreaterThan(rangeHigh)) {
                rangeHigh = previous.rangeHigh();
                rangeHighIndex = previous.rangeHighIndex();
            }
        }
        if (previous != null && !isInvalid(previous.rangeLow())) {
            if (isInvalid(rangeLow) || previous.rangeLow().isLessThan(rangeLow)) {
                rangeLow = previous.rangeLow();
                rangeLowIndex = previous.rangeLowIndex();
            }
        }

        final boolean hasRange = !isInvalid(rangeHigh) && !isInvalid(rangeLow);
        final boolean inRange = hasRange && !close.isGreaterThan(rangeHigh) && !close.isLessThan(rangeLow);
        final Num tolerance = hasRange ? rangeHigh.minus(rangeLow).multipliedBy(breakoutTolerance) : NaN;
        final Num breakoutAboveThreshold = hasRange ? rangeHigh.plus(tolerance) : NaN;
        final Num breakoutBelowThreshold = hasRange ? rangeLow.minus(tolerance) : NaN;
        final boolean brokeAbove = hasRange && !isInvalid(breakoutAboveThreshold)
                && close.isGreaterThan(breakoutAboveThreshold);
        final boolean brokeBelow = hasRange && !isInvalid(breakoutBelowThreshold)
                && close.isLessThan(breakoutBelowThreshold);
        return new StructureSnapshot(rangeLow, rangeHigh, rangeLowIndex, rangeHighIndex, close, inRange, brokeAbove,
                brokeBelow);
    }

    /**
     * Immutable view of the trading structure at a given bar.
     *
     * @param rangeLow        current trading-range low (NaN if unavailable)
     * @param rangeHigh       current trading-range high (NaN if unavailable)
     * @param rangeLowIndex   index of the swing low defining the range
     * @param rangeHighIndex  index of the swing high defining the range
     * @param closePrice      closing price at {@code index}
     * @param inRange         whether the close resides within the trading range
     * @param brokeAboveRange {@code true} if the close broke above the range high
     * @param brokeBelowRange {@code true} if the close broke below the range low
     * @since 0.19
     */
    public record StructureSnapshot(Num rangeLow, Num rangeHigh, int rangeLowIndex, int rangeHighIndex, Num closePrice,
            boolean inRange, boolean brokeAboveRange, boolean brokeBelowRange) {

        private static StructureSnapshot empty() {
            return new StructureSnapshot(NaN, NaN, -1, -1, NaN, false, false, false);
        }
    }

    private static boolean isInvalid(Num value) {
        if (value == null) {
            return true;
        }
        return value.isNaN() || Double.isNaN(value.doubleValue());
    }
}
