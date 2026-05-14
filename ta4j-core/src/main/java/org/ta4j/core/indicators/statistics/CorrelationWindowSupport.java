/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    static int unstableBars(int barCount, Indicator<?> first, Indicator<?> second) {
        int baseUnstableBars = Math.max(first.getCountOfUnstableBars(), second.getCountOfUnstableBars());
        return baseUnstableBars + barCount - 1;
    }

    static int unstableBars(int barCount, Indicator<?> first, Indicator<?> second, Indicator<?> third) {
        int baseUnstableBars = Math.max(first.getCountOfUnstableBars(), second.getCountOfUnstableBars());
        baseUnstableBars = Math.max(baseUnstableBars, third.getCountOfUnstableBars());
        return baseUnstableBars + barCount - 1;
    }

    static int laggedUnstableBars(int barCount, int lag, Indicator<?> first, Indicator<?> second) {
        int firstOffset = Math.max(lag, 0);
        int secondOffset = Math.max(-lag, 0);
        int firstUnstable = first.getCountOfUnstableBars() + firstOffset;
        int secondUnstable = second.getCountOfUnstableBars() + secondOffset;
        return Math.max(firstUnstable, secondUnstable) + barCount - 1;
    }

    static double[][] pairedWindow(Indicator<Num> first, Indicator<Num> second, int index, int barCount) {
        int startIndex = index - barCount + 1;
        if (!windowIsAvailable(first.getBarSeries(), startIndex, index)) {
            return null;
        }
        double[] firstValues = values(first, startIndex, barCount);
        double[] secondValues = values(second, startIndex, barCount);
        if (firstValues == null || secondValues == null) {
            return null;
        }
        return new double[][] { firstValues, secondValues };
    }

    static double[][] laggedWindow(Indicator<Num> first, Indicator<Num> second, int index, int barCount, int lag) {
        int secondEndIndex = lag >= 0 ? index : index + lag;
        int secondStartIndex = secondEndIndex - barCount + 1;
        int firstStartIndex = secondStartIndex - lag;
        int firstEndIndex = secondEndIndex - lag;
        BarSeries series = first.getBarSeries();
        if (!windowIsAvailable(series, secondStartIndex, secondEndIndex)
                || !windowIsAvailable(series, firstStartIndex, firstEndIndex)) {
            return null;
        }

        double[] firstValues = new double[barCount];
        double[] secondValues = new double[barCount];
        for (int i = 0; i < barCount; i++) {
            Num firstValue = first.getValue(firstStartIndex + i);
            Num secondValue = second.getValue(secondStartIndex + i);
            if (!isFinite(firstValue) || !isFinite(secondValue)) {
                return null;
            }
            firstValues[i] = firstValue.doubleValue();
            secondValues[i] = secondValue.doubleValue();
        }
        return new double[][] { firstValues, secondValues };
    }

    static double[][] activeRegimeWindow(Indicator<Num> first, Indicator<Num> second, Indicator<Boolean> regime,
            int index, int barCount) {
        int startIndex = index - barCount + 1;
        if (!windowIsAvailable(first.getBarSeries(), startIndex, index)) {
            return null;
        }

        List<Double> firstValues = new ArrayList<>();
        List<Double> secondValues = new ArrayList<>();
        for (int i = startIndex; i <= index; i++) {
            Boolean active = regime.getValue(i);
            if (!Boolean.TRUE.equals(active)) {
                continue;
            }
            Num firstValue = first.getValue(i);
            Num secondValue = second.getValue(i);
            if (!isFinite(firstValue) || !isFinite(secondValue)) {
                return null;
            }
            firstValues.add(firstValue.doubleValue());
            secondValues.add(secondValue.doubleValue());
        }

        double[] firstArray = new double[firstValues.size()];
        double[] secondArray = new double[secondValues.size()];
        for (int i = 0; i < firstValues.size(); i++) {
            firstArray[i] = firstValues.get(i);
            secondArray[i] = secondValues.get(i);
        }
        return new double[][] { firstArray, secondArray };
    }

    static Num pearson(NumFactory numFactory, double[] firstValues, double[] secondValues) {
        if (firstValues.length != secondValues.length || firstValues.length < 2) {
            return NaN.NaN;
        }

        double firstAverage = average(firstValues);
        double secondAverage = average(secondValues);
        double covariance = 0.0;
        double firstVariance = 0.0;
        double secondVariance = 0.0;
        for (int i = 0; i < firstValues.length; i++) {
            double firstDelta = firstValues[i] - firstAverage;
            double secondDelta = secondValues[i] - secondAverage;
            covariance += firstDelta * secondDelta;
            firstVariance += firstDelta * firstDelta;
            secondVariance += secondDelta * secondDelta;
        }

        double denominator = Math.sqrt(firstVariance * secondVariance);
        if (denominator <= 0.0 || Double.isNaN(denominator) || Double.isInfinite(denominator)) {
            return NaN.NaN;
        }
        return numFactory.numOf(covariance / denominator);
    }

    static double[] averageRanks(double[] values) {
        Integer[] indexes = new Integer[values.length];
        for (int i = 0; i < values.length; i++) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, (left, right) -> Double.compare(values[left], values[right]));

        double[] ranks = new double[values.length];
        int orderedIndex = 0;
        while (orderedIndex < indexes.length) {
            int tieEnd = orderedIndex;
            while (tieEnd + 1 < indexes.length
                    && Double.compare(values[indexes[orderedIndex]], values[indexes[tieEnd + 1]]) == 0) {
                tieEnd++;
            }
            double averageRank = ((double) orderedIndex + (double) tieEnd + 2.0) / 2.0;
            for (int i = orderedIndex; i <= tieEnd; i++) {
                ranks[indexes[i]] = averageRank;
            }
            orderedIndex = tieEnd + 1;
        }
        return ranks;
    }

    static boolean isFinite(Num value) {
        if (Num.isNaNOrNull(value)) {
            return false;
        }
        double primitive = value.doubleValue();
        return !Double.isNaN(primitive) && !Double.isInfinite(primitive);
    }

    private static boolean windowIsAvailable(BarSeries series, int startIndex, int endIndex) {
        return startIndex >= series.getBeginIndex() && endIndex <= series.getEndIndex() && startIndex <= endIndex;
    }

    private static double[] values(Indicator<Num> indicator, int startIndex, int barCount) {
        double[] values = new double[barCount];
        for (int i = 0; i < barCount; i++) {
            Num value = indicator.getValue(startIndex + i);
            if (!isFinite(value)) {
                return null;
            }
            values[i] = value.doubleValue();
        }
        return values;
    }

    private static double average(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }
}
