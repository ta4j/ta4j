/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Arrays;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

final class CorrelationWindowSupport {

    private CorrelationWindowSupport() {
    }

    /**
     * Rolling windows back the paired samples in {@code Num} arrays of the window
     * length, so every evaluation allocates at least two {@code Num[barCount]}
     * scratch arrays. The VM array limit alone would admit counts whose scratch
     * runs to hundreds of megabytes (or gigabytes) before the first evaluation,
     * so a practical shared ceiling rejects them up front. Indicators with
     * heavier per-bar working sets impose stricter bounds of their own (for
     * example {@link DynamicTimeWarpingDistanceIndicator}).
     */
    private static final int MAX_BAR_COUNT = 10_000_000;

    static int validateBarCount(int barCount) {
        if (barCount < 2) {
            throw new IllegalArgumentException("barCount must be >= 2");
        }
        if (barCount > MAX_BAR_COUNT) {
            throw new IllegalArgumentException("barCount exceeds the maximum window length");
        }
        return barCount;
    }

    static int validateBinCount(int binCount) {
        if (binCount < 2) {
            throw new IllegalArgumentException("binCount must be >= 2");
        }
        return binCount;
    }

    static int validateLag(int lag, int barCount) {
        long absoluteLag = Math.abs((long) lag);
        if (absoluteLag > Integer.MAX_VALUE - (long) barCount) {
            throw new IllegalArgumentException("absolute lag is too large for barCount");
        }
        return lag;
    }

    static int unstableBars(int barCount, Indicator<?> first, Indicator<?> second) {
        return clampUnstableBars(unstableBarsAsLong(barCount, first, second));
    }

    /**
     * The exact (un-clamped) unstable-bar boundary for a paired window: one bar
     * more than the largest unstable-bar count of either indicator. Long arithmetic
     * keeps the boundary exact beyond the int range so availability guards cannot
     * mistake a saturated published count for a reachable boundary at the extremes
     * of the index range.
     */
    static long unstableBarsAsLong(int barCount, Indicator<?> first, Indicator<?> second) {
        long baseUnstableBars = Math.max((long) first.getCountOfUnstableBars(), (long) second.getCountOfUnstableBars());
        return baseUnstableBars + (long) barCount - 1L;
    }

    static int unstableBars(int barCount, Indicator<?> first, Indicator<?> second, Indicator<?> third) {
        int baseUnstableBars = Math.max(first.getCountOfUnstableBars(), second.getCountOfUnstableBars());
        baseUnstableBars = Math.max(baseUnstableBars, third.getCountOfUnstableBars());
        long unstableBars = (long) baseUnstableBars + (long) barCount - 1L;
        return clampUnstableBars(unstableBars);
    }

    static int laggedUnstableBars(int barCount, int lag, Indicator<?> first, Indicator<?> second) {
        return clampUnstableBars(laggedUnstableBarsAsLong(barCount, lag, first, second));
    }

    /**
     * The exact (un-clamped) unstable-bar boundary for a window shifted by
     * {@code lag}: the worst of the two indicators' unstable-bar counts plus the
     * lag offset that pushes that indicator's window start latest, plus
     * {@code barCount - 1}. Long arithmetic keeps the boundary exact beyond the int
     * range so availability guards cannot mistake a saturated published count for a
     * reachable boundary at the extremes of the index range.
     */
    static long laggedUnstableBarsAsLong(int barCount, int lag, Indicator<?> first, Indicator<?> second) {
        long firstOffset = Math.max((long) lag, 0L);
        long secondOffset = Math.max(-(long) lag, 0L);
        long firstUnstable = (long) first.getCountOfUnstableBars() + firstOffset;
        long secondUnstable = (long) second.getCountOfUnstableBars() + secondOffset;
        long unstableBars = Math.max(firstUnstable, secondUnstable) + (long) barCount - 1L;
        return unstableBars;
    }

    static int clampUnstableBars(long unstableBars) {
        if (unstableBars > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (unstableBars < 0L) {
            return 0;
        }
        return (int) unstableBars;
    }

    static NumericWindow pairedWindow(Indicator<Num> first, Indicator<Num> second, int index, int barCount) {
        int startIndex = index - barCount + 1;
        if (!windowIsAvailable(first.getBarSeries(), startIndex, index)
                || !windowIsAvailable(second.getBarSeries(), startIndex, index)) {
            return null;
        }
        Num[] firstValues = values(first, startIndex, barCount);
        Num[] secondValues = values(second, startIndex, barCount);
        if (firstValues == null || secondValues == null) {
            return null;
        }
        return new NumericWindow(firstValues, secondValues, barCount);
    }

    static NumericWindow laggedWindow(Indicator<Num> first, Indicator<Num> second, int index, int barCount, int lag) {
        long secondEndIndexValue = lag >= 0 ? index : (long) index + lag;
        long secondStartIndexValue = secondEndIndexValue - (long) barCount + 1L;
        long firstStartIndexValue = secondStartIndexValue - lag;
        long firstEndIndexValue = secondEndIndexValue - lag;
        if (!isIntegerIndex(secondStartIndexValue) || !isIntegerIndex(secondEndIndexValue)
                || !isIntegerIndex(firstStartIndexValue) || !isIntegerIndex(firstEndIndexValue)) {
            return null;
        }

        int secondEndIndex = (int) secondEndIndexValue;
        int secondStartIndex = (int) secondStartIndexValue;
        int firstStartIndex = (int) firstStartIndexValue;
        int firstEndIndex = (int) firstEndIndexValue;
        if (!windowIsAvailable(first.getBarSeries(), firstStartIndex, firstEndIndex)
                || !windowIsAvailable(second.getBarSeries(), secondStartIndex, secondEndIndex)) {
            return null;
        }

        Num[] firstValues = new Num[barCount];
        Num[] secondValues = new Num[barCount];
        for (int i = 0; i < barCount; i++) {
            Num firstValue = first.getValue(firstStartIndex + i);
            Num secondValue = second.getValue(secondStartIndex + i);
            if (!Num.isFinite(firstValue) || !Num.isFinite(secondValue)) {
                return null;
            }
            firstValues[i] = firstValue;
            secondValues[i] = secondValue;
        }
        return new NumericWindow(firstValues, secondValues, barCount);
    }

    static NumericWindow activeRegimeWindow(Indicator<Num> first, Indicator<Num> second, Indicator<Boolean> regime,
            int index, int barCount) {
        int startIndex = index - barCount + 1;
        if (!windowIsAvailable(first.getBarSeries(), startIndex, index)
                || !windowIsAvailable(second.getBarSeries(), startIndex, index)
                || !windowIsAvailable(regime.getBarSeries(), startIndex, index)) {
            return null;
        }

        Num[] firstValues = new Num[barCount];
        Num[] secondValues = new Num[barCount];
        int sampleCount = 0;
        for (int i = startIndex; i <= index; i++) {
            Boolean active = regime.getValue(i);
            if (!Boolean.TRUE.equals(active)) {
                continue;
            }
            Num firstValue = first.getValue(i);
            Num secondValue = second.getValue(i);
            if (!Num.isFinite(firstValue) || !Num.isFinite(secondValue)) {
                continue;
            }
            firstValues[sampleCount] = firstValue;
            secondValues[sampleCount] = secondValue;
            sampleCount++;
        }
        return new NumericWindow(firstValues, secondValues, sampleCount);
    }

    static Num pearson(NumFactory numFactory, NumericWindow window) {
        return pearson(numFactory, window.firstValues(), window.secondValues(), window.sampleCount());
    }

    static Num pearson(NumFactory numFactory, Num[] firstValues, Num[] secondValues, int sampleCount) {
        if (firstValues.length != secondValues.length || sampleCount < 2) {
            return NaN.NaN;
        }

        // Pearson correlation is invariant under an independent rescaling of
        // each series, so every value is divided by its own series' largest
        // absolute value before averaging: the plain sum of extreme-but-finite
        // values (for example 1e308 and 1.1e308) would overflow to infinity,
        // while a single shared scale would underflow a much smaller series
        // (for example 1 and 2 next to 1e308 and 1.1e308) and square its
        // centered deviations to zero. Every scaled magnitude is <= 1, so the
        // means and centered sums stay finite and the ratio equals the
        // unscaled correlation.
        Num firstScale = numFactory.zero();
        Num secondScale = numFactory.zero();
        for (int i = 0; i < sampleCount; i++) {
            firstScale = firstScale.max(firstValues[i].abs());
            secondScale = secondScale.max(secondValues[i].abs());
        }
        if (firstScale.isZero() || secondScale.isZero()) {
            // A constant side has zero variance, which leaves the
            // correlation undefined.
            return NaN.NaN;
        }
        // Anchor-shifted two-pass centering: values are centered relative to
        // the window's first rescaled value instead of its mean. The mean of
        // near-endpoint values (for example 1 and Math.nextDown(1)) is not
        // exactly representable, and rounding it to an endpoint would lose the
        // deviations entirely: the two-sample correlation of
        // [Double.MAX_VALUE, Math.nextDown(Double.MAX_VALUE)] against [1, 0]
        // would report ~0.7071 instead of the exact 1. The deltas relative to
        // the anchor are exact, and their mean is a small value, so each
        // centered deviation is computed as delta minus that mean delta
        // without ever materializing the rescaled mean. The one-pass
        // identities (sum of squares minus square of sums) would cancel
        // catastrophically for near-constant windows, where the variance is
        // tiny relative to the squared deltas (DecimalNum's 16-digit context
        // would round it to exactly zero).
        Num firstAnchor = firstValues[0].dividedBy(firstScale);
        Num secondAnchor = secondValues[0].dividedBy(secondScale);
        Num firstDeltaSum = numFactory.zero();
        Num secondDeltaSum = numFactory.zero();
        for (int i = 0; i < sampleCount; i++) {
            firstDeltaSum = firstDeltaSum.plus(firstValues[i].dividedBy(firstScale).minus(firstAnchor));
            secondDeltaSum = secondDeltaSum.plus(secondValues[i].dividedBy(secondScale).minus(secondAnchor));
        }
        Num count = numFactory.numOf(sampleCount);
        Num firstMeanDelta = firstDeltaSum.dividedBy(count);
        Num secondMeanDelta = secondDeltaSum.dividedBy(count);
        Num covariance = numFactory.zero();
        Num firstVariance = numFactory.zero();
        Num secondVariance = numFactory.zero();
        for (int i = 0; i < sampleCount; i++) {
            Num firstCentered = firstValues[i].dividedBy(firstScale).minus(firstAnchor).minus(firstMeanDelta);
            Num secondCentered = secondValues[i].dividedBy(secondScale).minus(secondAnchor).minus(secondMeanDelta);
            covariance = covariance.plus(firstCentered.multipliedBy(secondCentered));
            firstVariance = firstVariance.plus(firstCentered.multipliedBy(firstCentered));
            secondVariance = secondVariance.plus(secondCentered.multipliedBy(secondCentered));
        }

        Num denominatorSquared = firstVariance.multipliedBy(secondVariance);
        if (!Num.isFinite(denominatorSquared) || !denominatorSquared.isPositive()) {
            return NaN.NaN;
        }
        Num denominator = denominatorSquared.sqrt();
        if (!Num.isFinite(denominator) || denominator.isZero()) {
            return NaN.NaN;
        }
        Num result = covariance.dividedBy(denominator);
        return Num.isFinite(result) ? result : NaN.NaN;
    }

    static Num[] averageRanks(NumFactory numFactory, Num[] values, int sampleCount) {
        Integer[] indexes = new Integer[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, (left, right) -> values[left].compareTo(values[right]));

        Num[] ranks = new Num[sampleCount];
        int orderedIndex = 0;
        while (orderedIndex < indexes.length) {
            int tieEnd = orderedIndex;
            while (tieEnd + 1 < indexes.length
                    && values[indexes[orderedIndex]].compareTo(values[indexes[tieEnd + 1]]) == 0) {
                tieEnd++;
            }
            Num averageRank = numFactory.numOf(orderedIndex + tieEnd + 2).dividedBy(numFactory.two());
            for (int i = orderedIndex; i <= tieEnd; i++) {
                ranks[indexes[i]] = averageRank;
            }
            orderedIndex = tieEnd + 1;
        }
        return ranks;
    }

    record NumericWindow(Num[] firstValues, Num[] secondValues, int sampleCount) {
    }

    private static boolean windowIsAvailable(BarSeries series, int startIndex, int endIndex) {
        return startIndex >= series.getBeginIndex() && endIndex <= series.getEndIndex() && startIndex <= endIndex;
    }

    private static boolean isIntegerIndex(long index) {
        return index >= Integer.MIN_VALUE && index <= Integer.MAX_VALUE;
    }

    private static Num[] values(Indicator<Num> indicator, int startIndex, int barCount) {
        Num[] values = new Num[barCount];
        for (int i = 0; i < barCount; i++) {
            Num value = indicator.getValue(startIndex + i);
            if (!Num.isFinite(value)) {
                return null;
            }
            values[i] = value;
        }
        return values;
    }
}
