/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Internal utility for shared fractal window detection logic.
 */
final class FractalDetectionHelper {

    enum Direction {
        HIGH {
            @Override
            boolean isCandidateDominant(Num candidate, Num comparison) {
                return candidate.isGreaterThan(comparison);
            }
        },
        LOW {
            @Override
            boolean isCandidateDominant(Num candidate, Num comparison) {
                return candidate.isLessThan(comparison);
            }
        };

        abstract boolean isCandidateDominant(Num candidate, Num comparison);
    }

    private FractalDetectionHelper() {
    }

    /**
     * Scans backwards from the latest confirmable candidate index and returns the
     * most recent confirmed fractal pivot.
     *
     * @param indicator         source indicator
     * @param series            backing series
     * @param maxAvailableIndex highest index whose future context is available
     * @param precedingBars     required dominating bars before candidate
     * @param followingBars     required dominating bars after candidate
     * @param allowedEqualBars  tolerated equal neighbors on each side
     * @param direction         fractal direction
     * @return latest confirmed pivot index, or {@code -1} when unavailable
     */
    static int findLatestConfirmedFractalIndex(Indicator<Num> indicator, BarSeries series, int maxAvailableIndex,
            int precedingBars, int followingBars, int allowedEqualBars, Direction direction) {
        if (indicator == null || series == null || direction == null) {
            return -1;
        }
        if (precedingBars < 0 || followingBars < 0 || allowedEqualBars < 0) {
            return -1;
        }
        final int beginIndex = series.getBeginIndex();
        final int endIndex = series.getEndIndex();
        if (maxAvailableIndex < beginIndex || maxAvailableIndex > endIndex) {
            return -1;
        }
        final int latestConfirmable = maxAvailableIndex - followingBars;
        final int earliestCandidate = beginIndex + precedingBars;
        if (latestConfirmable < earliestCandidate) {
            return -1;
        }
        for (int candidateIndex = latestConfirmable; candidateIndex >= earliestCandidate; candidateIndex--) {
            if (isConfirmedFractal(indicator, series, candidateIndex, precedingBars, followingBars, maxAvailableIndex,
                    allowedEqualBars, direction)) {
                return candidateIndex;
            }
        }
        return -1;
    }

    /**
     * Evaluates whether a specific candidate index is a confirmed fractal pivot.
     *
     * @param indicator         source indicator
     * @param series            backing series
     * @param candidateIndex    candidate pivot index
     * @param precedingBars     required dominating bars before candidate
     * @param followingBars     required dominating bars after candidate
     * @param maxAvailableIndex highest index whose future context is available
     * @param allowedEqualBars  tolerated equal neighbors on each side
     * @param direction         fractal direction
     * @return {@code true} when confirmed, otherwise {@code false}
     */
    static boolean isConfirmedFractal(Indicator<Num> indicator, BarSeries series, int candidateIndex, int precedingBars,
            int followingBars, int maxAvailableIndex, int allowedEqualBars, Direction direction) {
        if (indicator == null || series == null || direction == null) {
            return false;
        }
        if (precedingBars < 0 || followingBars < 0 || allowedEqualBars < 0) {
            return false;
        }
        final int beginIndex = series.getBeginIndex();
        final int endIndex = series.getEndIndex();
        if (candidateIndex < beginIndex || candidateIndex > maxAvailableIndex || candidateIndex > endIndex) {
            return false;
        }

        final Num candidateValue = indicator.getValue(candidateIndex);
        if (IndicatorUtils.isInvalid(candidateValue)) {
            return false;
        }

        final int plateauStart = findPlateauStart(indicator, beginIndex, candidateIndex, candidateValue,
                allowedEqualBars);
        if (plateauStart < 0) {
            return false;
        }
        final int plateauEnd = findPlateauEnd(indicator, candidateIndex, maxAvailableIndex, candidateValue,
                allowedEqualBars);
        if (plateauEnd < 0) {
            return false;
        }

        return hasDominatedPrecedingBars(indicator, beginIndex, plateauStart, precedingBars, candidateValue, direction)
                && hasDominatedFollowingBars(indicator, plateauEnd, maxAvailableIndex, followingBars, candidateValue,
                        direction);
    }

    private static int findPlateauStart(Indicator<Num> indicator, int beginIndex, int candidateIndex,
            Num candidateValue, int allowedEqualBars) {
        int equalsUsed = 0;
        int index = candidateIndex;
        while (index > beginIndex && equalsUsed < allowedEqualBars) {
            final Num previousValue = indicator.getValue(index - 1);
            if (IndicatorUtils.isInvalid(previousValue)) {
                return -1;
            }
            if (!previousValue.isEqual(candidateValue)) {
                break;
            }
            equalsUsed++;
            index--;
        }
        if (index > beginIndex) {
            final Num previousValue = indicator.getValue(index - 1);
            if (previousValue.isEqual(candidateValue)) {
                return -1;
            }
        }
        return index;
    }

    private static int findPlateauEnd(Indicator<Num> indicator, int candidateIndex, int maxAvailableIndex,
            Num candidateValue, int allowedEqualBars) {
        int equalsUsed = 0;
        int index = candidateIndex;
        while (index < maxAvailableIndex && equalsUsed < allowedEqualBars) {
            final Num nextValue = indicator.getValue(index + 1);
            if (IndicatorUtils.isInvalid(nextValue)) {
                return -1;
            }
            if (!nextValue.isEqual(candidateValue)) {
                break;
            }
            equalsUsed++;
            index++;
        }
        if (index < maxAvailableIndex) {
            final Num nextValue = indicator.getValue(index + 1);
            if (nextValue.isEqual(candidateValue)) {
                return -1;
            }
        }
        return index;
    }

    private static boolean hasDominatedPrecedingBars(Indicator<Num> indicator, int beginIndex, int plateauStartIndex,
            int precedingBars, Num candidateValue, Direction direction) {
        if (precedingBars == 0) {
            return true;
        }
        if (plateauStartIndex - precedingBars < beginIndex) {
            return false;
        }
        for (int i = plateauStartIndex - 1; i >= plateauStartIndex - precedingBars; i--) {
            final Num value = indicator.getValue(i);
            if (IndicatorUtils.isInvalid(value) || !direction.isCandidateDominant(candidateValue, value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasDominatedFollowingBars(Indicator<Num> indicator, int plateauEndIndex,
            int maxAvailableIndex, int followingBars, Num candidateValue, Direction direction) {
        if (followingBars == 0) {
            return true;
        }
        if (maxAvailableIndex - plateauEndIndex < followingBars) {
            return false;
        }
        for (int i = plateauEndIndex + 1; i <= plateauEndIndex + followingBars; i++) {
            if (i > maxAvailableIndex) {
                return false;
            }
            final Num value = indicator.getValue(i);
            if (IndicatorUtils.isInvalid(value) || !direction.isCandidateDominant(candidateValue, value)) {
                return false;
            }
        }
        return true;
    }
}
