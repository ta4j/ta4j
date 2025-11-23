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
package org.ta4j.core.indicators.supportresistance;

import static org.ta4j.core.num.NaN.NaN;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Abstract base for trend line indicators that rely on previously confirmed
 * swing highs or lows.
 * <p>
 * The indicator computes the best trend line for each non-overlapping window of
 * {@code barCount} bars walking backward from the latest bar. Only the line for
 * the latest window is valid for the most recent portion of the chart; earlier
 * windows are drawn as separate segments. When a new bar arrives, all previous
 * segments are discarded and recomputed.
 *
 * @since 0.20
 */
public abstract class AbstractTrendLineIndicator extends CachedIndicator<Num> {

    private final RecentSwingIndicator swingIndicator;
    private final Indicator<Num> priceIndicator;
    private final int barCount;
    private final int unstableBars;

    private transient List<TrendLineCandidate> cachedSegments = List.of();
    private transient int cachedEndIndex = Integer.MIN_VALUE;

    protected AbstractTrendLineIndicator(RecentSwingIndicator swingIndicator, int barCount, int unstableBars) {
        super(swingIndicator.getPriceIndicator());
        if (barCount < 2) {
            throw new IllegalArgumentException("barCount must be at least 2 to build a trend line");
        }
        this.swingIndicator = swingIndicator;
        this.priceIndicator = swingIndicator.getPriceIndicator();
        this.barCount = barCount;
        this.unstableBars = Math.max(0, unstableBars);
    }

    protected AbstractTrendLineIndicator(RecentSwingIndicator swingIndicator, int unstableBars) {
        this(swingIndicator, Integer.MAX_VALUE, unstableBars);
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    @Override
    protected Num calculate(int index) {
        if (index < getBarSeries().getBeginIndex() || index > getBarSeries().getEndIndex()) {
            return NaN;
        }
        final int endIndex = getBarSeries().getEndIndex();
        if (cachedEndIndex != endIndex) {
            cachedSegments = buildSegments(endIndex);
            cachedEndIndex = endIndex;
        }
        for (TrendLineCandidate candidate : cachedSegments) {
            if (candidate.windowStart <= index && index <= candidate.windowEnd) {
                return candidate.valueAt(index, getBarSeries().numFactory());
            }
        }
        return NaN;
    }

    private List<TrendLineCandidate> buildSegments(int endIndex) {
        final List<TrendLineCandidate> segments = new ArrayList<>();
        final int beginIndex = getBarSeries().getBeginIndex();
        int windowEnd = endIndex;
        final List<Integer> swingPoints = swingIndicator.getSwingPointIndexesUpTo(endIndex);
        while (windowEnd >= beginIndex) {
            final int windowStart = Math.max(beginIndex, windowEnd - barCount + 1);
            final TrendLineCandidate candidate = buildSegment(windowStart, windowEnd, swingPoints);
            if (candidate != null) {
                segments.add(candidate);
            }
            if (windowStart == beginIndex) {
                break;
            }
            windowEnd = windowStart - 1;
        }
        return segments;
    }

    private TrendLineCandidate buildSegment(int windowStart, int windowEnd, List<Integer> swingPoints) {
        final List<Integer> windowedSwings = new ArrayList<>();
        for (int idx : swingPoints) {
            if (idx >= windowStart && idx <= windowEnd) {
                windowedSwings.add(idx);
            }
        }
        if (windowedSwings.size() < 2) {
            return null;
        }
        final Num extremeSwingPrice = findExtremeSwingPrice(windowedSwings);
        final Num swingRange = findSwingRange(windowedSwings);
        if (isInvalid(extremeSwingPrice) || isInvalid(swingRange)) {
            return null;
        }
        final NumFactory numFactory = getBarSeries().numFactory();
        final Num priceAtEnd = resolvePriceAtIndex(windowEnd);
        return selectBestTrendLine(windowedSwings, windowEnd, extremeSwingPrice, swingRange, windowStart,
                windowEnd - windowStart + 1, numFactory, priceAtEnd);
    }

    /**
     * Returns the indexes of the confirmed swing points tracked by the indicator.
     */
    public List<Integer> getSwingPointIndexes() {
        return swingIndicator.getSwingPointIndexes();
    }

    protected abstract boolean isSupportLine();

    private Num resolvePriceAtIndex(int index) {
        final Num price = priceIndicator.getValue(index);
        if (!isInvalid(price)) {
            return price;
        }
        if (getBarSeries() == null || index < getBarSeries().getBeginIndex() || index > getBarSeries().getEndIndex()) {
            return price;
        }
        return isSupportLine() ? getBarSeries().getBar(index).getLowPrice()
                : getBarSeries().getBar(index).getHighPrice();
    }

    private TrendLineCandidate selectBestTrendLine(List<Integer> swingPointIndexes, int evaluationIndex,
            Num extremeSwingPrice, Num swingRange, int windowStart, int windowLength, NumFactory numFactory,
            Num priceAtIndex) {
        TrendLineCandidate bestCandidate = null;
        TrendLineCandidate bestContainingCandidate = null;

        for (int i = 0; i < swingPointIndexes.size() - 1; i++) {
            final int firstSwingIndex = swingPointIndexes.get(i);
            for (int j = i + 1; j < swingPointIndexes.size(); j++) {
                final int secondSwingIndex = swingPointIndexes.get(j);
                final TrendLineCandidate candidate = buildCandidate(firstSwingIndex, secondSwingIndex,
                        swingPointIndexes, evaluationIndex, numFactory, priceAtIndex, extremeSwingPrice, swingRange,
                        windowStart, windowLength);
                if (candidate == null) {
                    continue;
                }
                if (bestCandidate == null || candidate.isBetterThan(bestCandidate, priceAtIndex)) {
                    bestCandidate = candidate;
                }
                if (candidate.containsCurrentPrice && (bestContainingCandidate == null
                        || candidate.isBetterThan(bestContainingCandidate, priceAtIndex))) {
                    bestContainingCandidate = candidate;
                }
            }
        }
        if (bestCandidate == null) {
            return null;
        }
        if (bestContainingCandidate != null
                && Double.compare(bestContainingCandidate.score, bestCandidate.score) == 0) {
            return bestContainingCandidate;
        }
        return bestCandidate;
    }

    private TrendLineCandidate buildCandidate(int firstSwingIndex, int secondSwingIndex,
            List<Integer> swingPointIndexes, int evaluationIndex, NumFactory numFactory, Num priceAtEvaluation,
            Num extremeSwingPrice, Num swingRange, int windowStart, int windowLength) {
        final Num firstValue = priceIndicator.getValue(firstSwingIndex);
        final Num secondValue = priceIndicator.getValue(secondSwingIndex);
        if (isInvalid(firstValue) || isInvalid(secondValue)) {
            return null;
        }
        final Num x1 = numFactory.numOf(firstSwingIndex);
        final Num x2 = numFactory.numOf(secondSwingIndex);
        final Num denominator = x2.minus(x1);
        if (isInvalid(denominator) || denominator.isZero()) {
            return null;
        }
        final Num slope = secondValue.minus(firstValue).dividedBy(denominator);
        if (isInvalid(slope)) {
            return null;
        }
        final Num intercept = firstValue.minus(slope.multipliedBy(x1));
        if (isInvalid(intercept)) {
            return null;
        }

        int touches = 0;
        int outsideCount = 0;
        boolean touchesExtremeSwing = false;
        double sumDeviation = 0d;
        for (int swingIndex : swingPointIndexes) {
            final Num swingPrice = priceIndicator.getValue(swingIndex);
            if (isInvalid(swingPrice)) {
                return null;
            }
            final Num projectedAtSwing = slope.multipliedBy(numFactory.numOf(swingIndex)).plus(intercept);
            if (isInvalid(projectedAtSwing)) {
                return null;
            }
            final Num tolerance = toleranceForSwing(swingRange, numFactory);
            final boolean isAnchor = swingIndex == firstSwingIndex || swingIndex == secondSwingIndex;
            final Num deviation = projectedAtSwing.minus(swingPrice).abs();
            final boolean touchesSwing = isAnchor
                    || (!isInvalid(tolerance) && (deviation.isZero() || deviation.isLessThanOrEqual(tolerance)));
            if (touchesSwing) {
                touches++;
                if (!isInvalid(extremeSwingPrice) && swingPrice.isEqual(extremeSwingPrice)) {
                    touchesExtremeSwing = true;
                }
            } else {
                outsideCount++;
                if (isSupportLine()) {
                    if (projectedAtSwing.isGreaterThan(swingPrice)) {
                        return null;
                    }
                } else if (projectedAtSwing.isLessThan(swingPrice)) {
                    return null;
                }
            }
            final double deviationValue = Math.abs(projectedAtSwing.minus(swingPrice).doubleValue());
            sumDeviation += deviationValue;
        }

        final boolean containsCurrentPrice = containsCurrentPrice(priceAtEvaluation,
                slope.multipliedBy(numFactory.numOf(evaluationIndex)).plus(intercept));
        final int mostRecentAnchor = Math.max(firstSwingIndex, secondSwingIndex);
        final double recencyScore = Math.min(1d,
                Math.max(0d, (double) (mostRecentAnchor - windowStart) / windowLength));
        final double score = calculateScore(touches, swingPointIndexes.size(), touchesExtremeSwing, outsideCount,
                sumDeviation, swingRange.doubleValue(), recencyScore, containsCurrentPrice);

        return new TrendLineCandidate(firstSwingIndex, secondSwingIndex, slope, intercept, touches,
                containsCurrentPrice, outsideCount, touchesExtremeSwing, score, windowStart, evaluationIndex);
    }

    private boolean containsCurrentPrice(Num priceAtEvaluation, Num valueAtEvaluation) {
        if (isInvalid(priceAtEvaluation) || isInvalid(valueAtEvaluation)) {
            return false;
        }
        return isSupportLine() ? !valueAtEvaluation.isGreaterThan(priceAtEvaluation)
                : !valueAtEvaluation.isLessThan(priceAtEvaluation);
    }

    private boolean isInvalid(Num value) {
        return value == null || value.isNaN() || Double.isNaN(value.doubleValue());
    }

    private Num findSwingRange(List<Integer> swingPointIndexes) {
        Num min = null;
        Num max = null;
        for (int swingIndex : swingPointIndexes) {
            final Num swingPrice = priceIndicator.getValue(swingIndex);
            if (isInvalid(swingPrice)) {
                continue;
            }
            if (min == null || swingPrice.isLessThan(min)) {
                min = swingPrice;
            }
            if (max == null || swingPrice.isGreaterThan(max)) {
                max = swingPrice;
            }
        }
        if (min == null || max == null) {
            return NaN;
        }
        final Num range = max.minus(min);
        if (range.isZero()) {
            return getBarSeries().numFactory().numOf(1e-9);
        }
        return range;
    }

    private Num toleranceForSwing(Num swingRange, NumFactory numFactory) {
        if (isInvalid(swingRange)) {
            return NaN;
        }
        final Num percentage = numFactory.numOf(0.02);
        final Num tolerance = swingRange.multipliedBy(percentage);
        final Num minimum = numFactory.numOf(1e-9);
        if (tolerance.isNaN() || tolerance.isZero()) {
            return minimum;
        }
        return tolerance.isLessThan(minimum) ? minimum : tolerance;
    }

    private Num findExtremeSwingPrice(List<Integer> swingPointIndexes) {
        Num extreme = null;
        for (int swingIndex : swingPointIndexes) {
            final Num swingPrice = priceIndicator.getValue(swingIndex);
            if (isInvalid(swingPrice)) {
                continue;
            }
            if (extreme == null) {
                extreme = swingPrice;
                continue;
            }
            if (isSupportLine()) {
                if (swingPrice.isLessThan(extreme)) {
                    extreme = swingPrice;
                }
            } else if (swingPrice.isGreaterThan(extreme)) {
                extreme = swingPrice;
            }
        }
        return extreme == null ? NaN : extreme;
    }

    private double calculateScore(int touches, int totalSwings, boolean touchesExtreme, int outsideCount,
            double sumDeviation, double swingRange, double recencyScore, boolean containsCurrentPrice) {
        if (totalSwings <= 0) {
            return 0d;
        }
        final double touchScore = (double) touches / (double) totalSwings;
        final double extremeScore = touchesExtreme ? 1d : 0d;
        final double outsidePenalty = Math.min(outsideCount, totalSwings);
        final double outsideScore = 1d - (outsidePenalty / (double) totalSwings);
        final double averageDeviation = totalSwings == 0 ? 0d : sumDeviation / (double) totalSwings;
        final boolean invalidRange = Double.isNaN(swingRange) || swingRange <= 0d;
        final double normalizedDeviation = invalidRange ? 0d : Math.min(1d, averageDeviation / swingRange);
        final double proximityScore = 1d - normalizedDeviation;
        final double baseScore = 0.30d * touchScore + 0.20d * extremeScore + 0.15d * outsideScore
                + 0.20d * proximityScore + 0.10d * recencyScore;
        final double containBonus = containsCurrentPrice ? 0.05d : 0d;
        return baseScore + containBonus;
    }

    private final class TrendLineCandidate {
        private final int firstIndex;
        private final int secondIndex;
        private final Num slope;
        private final Num intercept;
        private final int touches;
        private final boolean containsCurrentPrice;
        private final int outsideCount;
        private final boolean touchesExtreme;
        private final double score;
        private final int windowStart;
        private final int windowEnd;

        private TrendLineCandidate(int firstIndex, int secondIndex, Num slope, Num intercept, int touches,
                boolean containsCurrentPrice, int outsideCount, boolean touchesExtreme, double score, int windowStart,
                int windowEnd) {
            this.firstIndex = firstIndex;
            this.secondIndex = secondIndex;
            this.slope = slope;
            this.intercept = intercept;
            this.touches = touches;
            this.containsCurrentPrice = containsCurrentPrice;
            this.outsideCount = outsideCount;
            this.touchesExtreme = touchesExtreme;
            this.score = score;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
        }

        private boolean isBetterThan(TrendLineCandidate other, Num priceAtEvaluation) {
            if (other == null) {
                return true;
            }
            if (Double.compare(this.score, other.score) != 0) {
                return this.score > other.score;
            }
            if (this.containsCurrentPrice != other.containsCurrentPrice) {
                return this.containsCurrentPrice;
            }
            if (this.touches != other.touches) {
                return this.touches > other.touches;
            }
            if (this.outsideCount != other.outsideCount) {
                return this.outsideCount < other.outsideCount;
            }
            final int span = this.secondIndex - this.firstIndex;
            final int otherSpan = other.secondIndex - other.firstIndex;
            if (span != otherSpan) {
                return span > otherSpan;
            }
            if (priceAtEvaluation != null && !priceAtEvaluation.isNaN()) {
                final Num thisValue = valueAt(windowEnd, priceAtEvaluation.getNumFactory());
                final Num otherValue = other.valueAt(other.windowEnd, priceAtEvaluation.getNumFactory());
                final Num distance = thisValue.minus(priceAtEvaluation).abs();
                final Num otherDistance = otherValue.minus(priceAtEvaluation).abs();
                if (distance.isLessThan(otherDistance)) {
                    return true;
                }
                if (distance.isGreaterThan(otherDistance)) {
                    return false;
                }
            }
            return this.secondIndex > other.secondIndex;
        }

        private TrendLineCandidate withWindow(int scoringWindowStart, int windowEnd) {
            return new TrendLineCandidate(firstIndex, secondIndex, slope, intercept, touches, containsCurrentPrice,
                    outsideCount, touchesExtreme, score, scoringWindowStart, windowEnd);
        }

        private Num valueAt(int index, NumFactory numFactory) {
            return slope.multipliedBy(numFactory.numOf(index)).plus(intercept);
        }
    }

    /**
     * Returns the indexes of the confirmed pivot points tracked by the indicator.
     *
     * @return an immutable list containing the pivot indexes in chronological order
     * @deprecated Use {@link #getSwingPointIndexes()} instead. This method will be
     *             removed in a future version.
     * @since 0.20
     */
    @Deprecated
    public List<Integer> getPivotIndexes() {
        return getSwingPointIndexes();
    }
}
