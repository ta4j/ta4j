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
     * @param allowedEqualBars  maximum additional equal bars in a plateau
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
        final int earliestCandidate = beginIndex + precedingBars;
        if (maxAvailableIndex < earliestCandidate) {
            return -1;
        }
        for (int candidateIndex = maxAvailableIndex; candidateIndex >= earliestCandidate; candidateIndex--) {
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
     * @param allowedEqualBars  maximum additional equal bars in a plateau
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
        if (!Num.isFinite(candidateValue)) {
            return false;
        }

        final int plateauStart = findPlateauStart(indicator, beginIndex, candidateIndex, candidateValue);
        if (plateauStart < 0) {
            return false;
        }
        final int plateauEnd = findPlateauEnd(indicator, candidateIndex, maxAvailableIndex, candidateValue);
        if (plateauEnd < 0) {
            return false;
        }

        final int plateauLength = plateauEnd - plateauStart + 1;
        final int canonicalIndex = plateauStart + (plateauLength - 1) / 2;
        return candidateIndex == canonicalIndex && plateauLength - 1 <= allowedEqualBars
                && hasDominatedPrecedingBars(indicator, beginIndex, plateauStart, precedingBars, candidateValue,
                        direction)
                && hasDominatedFollowingBars(indicator, plateauEnd, maxAvailableIndex, followingBars, candidateValue,
                        direction);
    }

    private static int findPlateauStart(Indicator<Num> indicator, int beginIndex, int candidateIndex,
            Num candidateValue) {
        int index = candidateIndex;
        while (index > beginIndex) {
            final Num previousValue = indicator.getValue(index - 1);
            if (!Num.isFinite(previousValue)) {
                return -1;
            }
            if (!previousValue.isEqual(candidateValue)) {
                break;
            }
            index--;
        }
        return index;
    }

    private static int findPlateauEnd(Indicator<Num> indicator, int candidateIndex, int maxAvailableIndex,
            Num candidateValue) {
        int index = candidateIndex;
        while (index < maxAvailableIndex) {
            final Num nextValue = indicator.getValue(index + 1);
            if (!Num.isFinite(nextValue)) {
                return -1;
            }
            if (!nextValue.isEqual(candidateValue)) {
                break;
            }
            index++;
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
            if (!Num.isFinite(value) || !direction.isCandidateDominant(candidateValue, value)) {
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
            if (!Num.isFinite(value) || !direction.isCandidateDominant(candidateValue, value)) {
                return false;
            }
        }
        return true;
    }
}
