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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Abstract base for trend line indicators that rely on previously confirmed
 * swing highs or lows.
 * <p>
 * The indicator computes the best trend line for the latest window of
 * {@code barCount} bars anchored at the series end. Bars that fall outside the
 * look-back window return {@code NaN}. When a new bar arrives, the current
 * trend line is recomputed for the new window.
 *
 * @since 0.20
 */
public abstract class AbstractTrendLineIndicator extends CachedIndicator<Num> {

    private final RecentSwingIndicator swingIndicator;
    private final transient Indicator<Num> priceIndicator;
    private final int barCount;
    private final int unstableBars;
    private final TrendLineSide side;
    private final double touchWeight;
    private final double extremeWeight;
    private final double outsideWeight;
    private final double proximityWeight;
    private final double recencyWeight;
    private final double containBonus;
    private final ScoringWeights scoringWeights;

    private transient TrendLineCandidate cachedSegment;
    private transient int cachedEndIndex = Integer.MIN_VALUE;
    private transient int cachedWindowStart = Integer.MIN_VALUE;
    private transient int cachedRemovedBars = Integer.MIN_VALUE;
    private transient Map<Integer, Num> valueCache = new HashMap<>();
    private transient long coordinateBaseEpochMillis = Long.MIN_VALUE;
    private transient int coordinateBaseIndex = Integer.MIN_VALUE;

    protected AbstractTrendLineIndicator(RecentSwingIndicator swingIndicator, int barCount, int unstableBars,
            TrendLineSide side, ScoringWeights scoringWeights) {
        this(swingIndicator, barCount, unstableBars, side, resolve(scoringWeights).touchWeight,
                resolve(scoringWeights).extremeWeight, resolve(scoringWeights).outsideWeight,
                resolve(scoringWeights).proximityWeight, resolve(scoringWeights).recencyWeight,
                resolve(scoringWeights).containBonus);
    }

    protected AbstractTrendLineIndicator(RecentSwingIndicator swingIndicator, int barCount, int unstableBars,
            TrendLineSide side, double touchWeight, double extremeWeight, double outsideWeight, double proximityWeight,
            double recencyWeight, double containBonus) {
        super(swingIndicator.getPriceIndicator());
        if (barCount < 2) {
            throw new IllegalArgumentException("barCount must be at least 2 to build a trend line");
        }
        this.swingIndicator = swingIndicator;
        this.priceIndicator = swingIndicator.getPriceIndicator();
        this.barCount = barCount;
        this.unstableBars = Math.max(0, unstableBars);
        this.side = side;
        this.touchWeight = touchWeight;
        this.extremeWeight = extremeWeight;
        this.outsideWeight = outsideWeight;
        this.proximityWeight = proximityWeight;
        this.recencyWeight = recencyWeight;
        this.containBonus = containBonus;
        this.scoringWeights = new ScoringWeights(touchWeight, extremeWeight, outsideWeight, proximityWeight,
                recencyWeight, containBonus);
    }

    protected AbstractTrendLineIndicator(RecentSwingIndicator swingIndicator, int unstableBars, TrendLineSide side,
            ScoringWeights scoringWeights) {
        this(swingIndicator, Integer.MAX_VALUE, unstableBars, side, scoringWeights);
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    @Override
    public synchronized Num getValue(int index) {
        if (getBarSeries() == null) {
            return NaN;
        }
        refreshCachedState();
        return valueCache.computeIfAbsent(index, this::calculate);
    }

    @Override
    protected Num calculate(int index) {
        final int beginIndex = getBarSeries().getBeginIndex();
        final int endIndex = getBarSeries().getEndIndex();
        if (index < beginIndex || index > endIndex) {
            return NaN;
        }
        final int windowStart = Math.max(beginIndex, endIndex - barCount + 1);
        if (index < windowStart) {
            return NaN;
        }
        if (cachedSegment == null || cachedEndIndex != endIndex || cachedWindowStart != windowStart) {
            cachedSegment = buildSegment(windowStart, endIndex, swingIndicator.getSwingPointIndexesUpTo(endIndex));
            cachedEndIndex = endIndex;
            cachedWindowStart = windowStart;
        }
        if (cachedSegment == null) {
            return NaN;
        }
        return cachedSegment.valueAt(index, getBarSeries().numFactory());
    }

    private void refreshCachedState() {
        final int endIndex = getBarSeries().getEndIndex();
        final int removedBars = getBarSeries().getRemovedBarsCount();
        if (endIndex != cachedEndIndex || removedBars != cachedRemovedBars) {
            valueCache = new HashMap<>();
            cachedSegment = null;
            cachedWindowStart = Integer.MIN_VALUE;
            cachedEndIndex = Integer.MIN_VALUE;
            cachedRemovedBars = removedBars;
        }
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

    private Num resolvePriceAtIndex(int index) {
        final Num price = priceIndicator.getValue(index);
        if (!isInvalid(price)) {
            return price;
        }
        if (getBarSeries() == null || index < getBarSeries().getBeginIndex() || index > getBarSeries().getEndIndex()) {
            return price;
        }
        return side.selectBarPrice(getBarSeries().getBar(index));
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
        final Num x1 = coordinateForIndex(firstSwingIndex, numFactory);
        final Num x2 = coordinateForIndex(secondSwingIndex, numFactory);
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
            final Num projectedAtSwing = slope.multipliedBy(coordinateForIndex(swingIndex, numFactory)).plus(intercept);
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
                if (side.violates(projectedAtSwing, swingPrice)) {
                    return null;
                }
            }
            final double deviationValue = Math.abs(projectedAtSwing.minus(swingPrice).doubleValue());
            sumDeviation += deviationValue;
        }

        final boolean containsCurrentPrice = side.contains(
                slope.multipliedBy(coordinateForIndex(evaluationIndex, numFactory)).plus(intercept), priceAtEvaluation);
        final int mostRecentAnchor = Math.max(firstSwingIndex, secondSwingIndex);
        final double recencyScore = Math.min(1d,
                Math.max(0d, (double) (mostRecentAnchor - windowStart) / windowLength));
        final double score = calculateScore(touches, swingPointIndexes.size(), touchesExtremeSwing, outsideCount,
                sumDeviation, swingRange.doubleValue(), recencyScore, containsCurrentPrice);

        return new TrendLineCandidate(firstSwingIndex, secondSwingIndex, slope, intercept, touches,
                containsCurrentPrice, outsideCount, touchesExtremeSwing, score, windowStart, evaluationIndex);
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
        if (min == null) {
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
            extreme = side.pickExtreme(extreme, swingPrice);
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
        final double averageDeviation = sumDeviation / (double) totalSwings;
        final boolean invalidRange = Double.isNaN(swingRange) || swingRange <= 0d;
        final double normalizedDeviation = invalidRange ? 0d : Math.min(1d, averageDeviation / swingRange);
        final double proximityScore = 1d - normalizedDeviation;
        final double baseScore = touchWeight * touchScore + extremeWeight * extremeScore + outsideWeight * outsideScore
                + proximityWeight * proximityScore + recencyWeight * recencyScore;
        final double containBonusScore = containsCurrentPrice ? containBonus : 0d;
        return baseScore + containBonusScore;
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

        private Num valueAt(int index, NumFactory numFactory) {
            final Num coordinate = coordinateForIndex(index, numFactory);
            return slope.multipliedBy(coordinate).plus(intercept);
        }
    }

    protected enum TrendLineSide {
        SUPPORT {
            @Override
            boolean violates(Num projected, Num swingPrice) {
                return projected.isGreaterThan(swingPrice);
            }

            @Override
            boolean contains(Num value, Num price) {
                return !value.isGreaterThan(price);
            }

            @Override
            Num pickExtreme(Num currentExtreme, Num candidate) {
                if (currentExtreme == null || candidate.isLessThan(currentExtreme)) {
                    return candidate;
                }
                return currentExtreme;
            }

            @Override
            Num selectBarPrice(Bar bar) {
                return bar.getLowPrice();
            }
        },
        RESISTANCE {
            @Override
            boolean violates(Num projected, Num swingPrice) {
                return projected.isLessThan(swingPrice);
            }

            @Override
            boolean contains(Num value, Num price) {
                return !value.isLessThan(price);
            }

            @Override
            Num pickExtreme(Num currentExtreme, Num candidate) {
                if (currentExtreme == null || candidate.isGreaterThan(currentExtreme)) {
                    return candidate;
                }
                return currentExtreme;
            }

            @Override
            Num selectBarPrice(Bar bar) {
                return bar.getHighPrice();
            }
        };

        abstract boolean violates(Num projected, Num swingPrice);

        abstract boolean contains(Num value, Num price);

        abstract Num pickExtreme(Num currentExtreme, Num candidate);

        abstract Num selectBarPrice(org.ta4j.core.Bar bar);
    }

    public ScoringWeights getScoringWeights() {
        return scoringWeights;
    }

    private static ScoringWeights resolve(ScoringWeights scoringWeights) {
        return scoringWeights == null ? ScoringWeights.defaultWeights() : scoringWeights;
    }

    private void refreshCoordinateBase() {
        final var series = getBarSeries();
        if (series == null || series.isEmpty()) {
            coordinateBaseIndex = Integer.MIN_VALUE;
            coordinateBaseEpochMillis = Long.MIN_VALUE;
            return;
        }
        final int beginIndex = series.getBeginIndex();
        if (coordinateBaseIndex != beginIndex) {
            coordinateBaseIndex = beginIndex;
            coordinateBaseEpochMillis = resolveEndTimeMillis(beginIndex);
        }
    }

    private Num coordinateForIndex(int index, NumFactory numFactory) {
        final var series = getBarSeries();
        if (series == null || index < series.getBeginIndex() || index > series.getEndIndex()) {
            return numFactory.numOf(index);
        }
        refreshCoordinateBase();
        final long epochMillis = resolveEndTimeMillis(index);
        return numFactory.numOf(epochMillis - coordinateBaseEpochMillis);
    }

    private long resolveEndTimeMillis(int index) {
        final var series = getBarSeries();
        if (series == null || index < series.getBeginIndex() || index > series.getEndIndex()) {
            return 0L;
        }
        final Bar bar = series.getBar(index);
        return bar.getEndTime().toEpochMilli();
    }

    public static final class ScoringWeights {
        /**
         * All weights are fractional percentages between 0.0 and 1.0 and must sum to
         * 1.0 (100%). These values are serialized in indicator descriptors/JSON so user
         * preferences round-trip.
         */
        public final double touchWeight;
        public final double extremeWeight;
        public final double outsideWeight;
        public final double proximityWeight;
        public final double recencyWeight;
        public final double containBonus;

        private ScoringWeights(double touchWeight, double extremeWeight, double outsideWeight, double proximityWeight,
                double recencyWeight, double containBonus) {
            this.touchWeight = touchWeight;
            this.extremeWeight = extremeWeight;
            this.outsideWeight = outsideWeight;
            this.proximityWeight = proximityWeight;
            this.recencyWeight = recencyWeight;
            this.containBonus = containBonus;
            validateWeights();
        }

        public static ScoringWeights defaultWeights() {
            return new ScoringWeights(0.30d, 0.20d, 0.15d, 0.20d, 0.10d, 0.05d);
        }

        /**
         * Creates scoring weights from explicit fractional percentages.
         */
        public static ScoringWeights of(double touchWeight, double extremeWeight, double outsideWeight,
                double proximityWeight, double recencyWeight, double containBonus) {
            return new ScoringWeights(touchWeight, extremeWeight, outsideWeight, proximityWeight, recencyWeight,
                    containBonus);
        }

        /**
         * Heavier emphasis on touching swing points with moderate proximity/outside
         * penalties.
         */
        public static ScoringWeights touchHeavyPreset() {
            return new ScoringWeights(0.50d, 0.15d, 0.10d, 0.10d, 0.05d, 0.10d);
        }

        /**
         * Heavier emphasis on touching the extreme swing while keeping lines reasonably
         * close to price action.
         */
        public static ScoringWeights extremeHeavyPreset() {
            return new ScoringWeights(0.35d, 0.35d, 0.10d, 0.10d, 0.05d, 0.05d);
        }

        /**
         * Alias for {@link #defaultWeights()} to emphasize these represent scoring
         * weights.
         */
        public static ScoringWeights defaultScoringWeights() {
            return defaultWeights();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private double touchWeight = 0.30d;
            private double extremeWeight = 0.20d;
            private double outsideWeight = 0.15d;
            private double proximityWeight = 0.20d;
            private double recencyWeight = 0.10d;
            private double containBonus = 0.05d;

            /**
             * Fractional weight for favoring lines that touch the most swing points.
             *
             * @param weightFraction percentage expressed as a fraction between 0.0 and 1.0
             */
            public Builder weightForTouchingSwingPoints(double weightFraction) {
                this.touchWeight = weightFraction;
                return this;
            }

            /**
             * Fractional weight for preferring lines that also touch the most extreme swing
             * (highest high or lowest low) in the window.
             *
             * @param weightFraction percentage expressed as a fraction between 0.0 and 1.0
             */
            public Builder weightForTouchingExtremeSwing(double weightFraction) {
                this.extremeWeight = weightFraction;
                return this;
            }

            /**
             * Fractional weight for minimizing swing points that sit outside the line.
             *
             * @param weightFraction percentage expressed as a fraction between 0.0 and 1.0
             */
            public Builder weightForKeepingSwingsInsideLine(double weightFraction) {
                this.outsideWeight = weightFraction;
                return this;
            }

            /**
             * Fractional weight for keeping the line close to swing prices on average
             * (proximity).
             *
             * @param weightFraction percentage expressed as a fraction between 0.0 and 1.0
             */
            public Builder weightForStayingCloseToSwings(double weightFraction) {
                this.proximityWeight = weightFraction;
                return this;
            }

            /**
             * Fractional weight for preferring more recent anchor points within the
             * lookback window.
             *
             * @param weightFraction percentage expressed as a fraction between 0.0 and 1.0
             */
            public Builder weightForRecentAnchorPoints(double weightFraction) {
                this.recencyWeight = weightFraction;
                return this;
            }

            /**
             * Fractional bonus applied when the line contains the current price (below the
             * current low for support, above the current high for resistance).
             *
             * @param bonusFraction percentage expressed as a fraction between 0.0 and 1.0
             */
            public Builder bonusForContainingCurrentPrice(double bonusFraction) {
                this.containBonus = bonusFraction;
                return this;
            }

            /**
             * Builds validated {@link ScoringWeights} ensuring all values are within [0.0,
             * 1.0] and collectively sum to 1.0.
             */
            public ScoringWeights build() {
                return new ScoringWeights(touchWeight, extremeWeight, outsideWeight, proximityWeight, recencyWeight,
                        containBonus);
            }
        }

        private void validateWeights() {
            validateFraction(touchWeight, "touchWeight");
            validateFraction(extremeWeight, "extremeWeight");
            validateFraction(outsideWeight, "outsideWeight");
            validateFraction(proximityWeight, "proximityWeight");
            validateFraction(recencyWeight, "recencyWeight");
            validateFraction(containBonus, "containBonus");
            final double sum = touchWeight + extremeWeight + outsideWeight + proximityWeight + recencyWeight
                    + containBonus;
            final double epsilon = 1e-6;
            if (Math.abs(1.0d - sum) > epsilon) {
                throw new IllegalArgumentException(
                        String.format("Scoring weights must sum to 1.0 (100%%). Got %.6f", sum));
            }
        }

        private void validateFraction(double value, String label) {
            if (Double.isNaN(value) || value < 0.0d || value > 1.0d) {
                throw new IllegalArgumentException(String
                        .format("%s must be between 0.0 and 1.0 (fractional percentage). Got %.6f", label, value));
            }
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
