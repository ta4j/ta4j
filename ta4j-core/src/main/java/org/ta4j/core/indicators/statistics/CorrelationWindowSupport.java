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

    static int validateBarCount(int barCount) {
        if (barCount < 2) {
            throw new IllegalArgumentException("barCount must be >= 2");
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
        int baseUnstableBars = Math.max(first.getCountOfUnstableBars(), second.getCountOfUnstableBars());
        long unstableBars = (long) baseUnstableBars + (long) barCount - 1L;
        return clampUnstableBars(unstableBars);
    }

    static int unstableBars(int barCount, Indicator<?> first, Indicator<?> second, Indicator<?> third) {
        int baseUnstableBars = Math.max(first.getCountOfUnstableBars(), second.getCountOfUnstableBars());
        baseUnstableBars = Math.max(baseUnstableBars, third.getCountOfUnstableBars());
        long unstableBars = (long) baseUnstableBars + (long) barCount - 1L;
        return clampUnstableBars(unstableBars);
    }

    static int laggedUnstableBars(int barCount, int lag, Indicator<?> first, Indicator<?> second) {
        long firstOffset = Math.max((long) lag, 0L);
        long secondOffset = Math.max(-(long) lag, 0L);
        long firstUnstable = (long) first.getCountOfUnstableBars() + firstOffset;
        long secondUnstable = (long) second.getCountOfUnstableBars() + secondOffset;
        long unstableBars = Math.max(firstUnstable, secondUnstable) + (long) barCount - 1L;
        return clampUnstableBars(unstableBars);
    }

    private static int clampUnstableBars(long unstableBars) {
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
            if (!isFinite(firstValue) || !isFinite(secondValue)) {
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
            if (!isFinite(firstValue) || !isFinite(secondValue)) {
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

        Num firstAverage = average(numFactory, firstValues, sampleCount);
        Num secondAverage = average(numFactory, secondValues, sampleCount);
        Num covariance = numFactory.zero();
        Num firstVariance = numFactory.zero();
        Num secondVariance = numFactory.zero();
        for (int i = 0; i < sampleCount; i++) {
            Num firstDelta = firstValues[i].minus(firstAverage);
            Num secondDelta = secondValues[i].minus(secondAverage);
            covariance = covariance.plus(firstDelta.multipliedBy(secondDelta));
            firstVariance = firstVariance.plus(firstDelta.multipliedBy(firstDelta));
            secondVariance = secondVariance.plus(secondDelta.multipliedBy(secondDelta));
        }

        Num denominatorSquared = firstVariance.multipliedBy(secondVariance);
        if (!isFinite(denominatorSquared) || !denominatorSquared.isPositive()) {
            return NaN.NaN;
        }
        Num denominator = denominatorSquared.sqrt();
        if (!isFinite(denominator) || denominator.isZero()) {
            return NaN.NaN;
        }
        Num result = covariance.dividedBy(denominator);
        return isFinite(result) ? result : NaN.NaN;
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

    static boolean isFinite(Num value) {
        if (value == null || value.isNaN()) {
            return false;
        }
        Number delegate = value.getDelegate();
        if (delegate instanceof Double primitive) {
            return Double.isFinite(primitive);
        }
        if (delegate instanceof Float primitive) {
            return Float.isFinite(primitive);
        }
        return true;
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
            if (!isFinite(value)) {
                return null;
            }
            values[i] = value;
        }
        return values;
    }

    private static Num average(NumFactory numFactory, Num[] values, int sampleCount) {
        Num sum = numFactory.zero();
        for (int i = 0; i < sampleCount; i++) {
            sum = sum.plus(values[i]);
        }
        return sum.dividedBy(numFactory.numOf(sampleCount));
    }
}
