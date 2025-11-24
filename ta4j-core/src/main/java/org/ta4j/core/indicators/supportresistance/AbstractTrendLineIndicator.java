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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

/**
 * Abstract base for trend line indicators that rely on previously confirmed
 * swing highs or lows.
 * <p>
 * The indicator computes the best trend line for the latest window of
 * {@code barCount} bars anchored at the series end. Bars that fall outside the
 * look-back window return {@code NaN}. Bars inside the window (including the
 * {@code windowStart}) return the active line when at least two swing points
 * exist; otherwise, they return {@code NaN}. When a new bar arrives, the
 * current trend line is recomputed for the new window.
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
    private final ScoringWeights scoringWeights;
    private final ToleranceSettings toleranceSettings;

    private transient TrendLineCandidate cachedSegment;
    private transient int cachedEndIndex = Integer.MIN_VALUE;
    private transient int cachedWindowStart = Integer.MIN_VALUE;
    private transient int cachedRemovedBars = Integer.MIN_VALUE;
    private transient Map<Integer, Num> valueCache = new HashMap<>();
    private transient List<Integer> cachedWindowSwings = List.of();
    private transient List<CandidateGeometry> cachedGeometries = List.of();
    private transient long coordinateBaseEpochMillis = Long.MIN_VALUE;
    private transient int coordinateBaseIndex = Integer.MIN_VALUE;

    protected AbstractTrendLineIndicator(RecentSwingIndicator swingIndicator, int barCount, int unstableBars,
            TrendLineSide side, ScoringWeights scoringWeights) {
        this(swingIndicator, barCount, unstableBars, side, resolve(scoringWeights).touchWeight,
                resolve(scoringWeights).extremeWeight, resolve(scoringWeights).outsideWeight,
                resolve(scoringWeights).proximityWeight, resolve(scoringWeights).recencyWeight,
                ToleranceSettings.defaultSettings());
    }

    protected AbstractTrendLineIndicator(RecentSwingIndicator swingIndicator, int barCount, int unstableBars,
            TrendLineSide side, double touchWeight, double extremeWeight, double outsideWeight, double proximityWeight,
            double recencyWeight, ToleranceSettings toleranceSettings) {
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
        this.scoringWeights = new ScoringWeights(touchWeight, extremeWeight, outsideWeight, proximityWeight,
                recencyWeight);
        this.toleranceSettings = toleranceSettings == null ? ToleranceSettings.defaultSettings() : toleranceSettings;
    }

    protected AbstractTrendLineIndicator(RecentSwingIndicator swingIndicator, int unstableBars, TrendLineSide side,
            ScoringWeights scoringWeights) {
        this(swingIndicator, Integer.MAX_VALUE, unstableBars, side, scoringWeights);
    }

    @Override
    public int getCountOfUnstableBars() {
        final Indicator<Num> source = swingIndicator;
        final int sourceUnstable = source == null ? 0 : source.getCountOfUnstableBars();
        return Math.max(unstableBars, sourceUnstable);
    }

    @Override
    public synchronized Num getValue(int index) {
        if (getBarSeries() == null) {
            return NaN;
        }
        refreshCachedState();
        final Num cached = valueCache.get(index);
        if (cached != null) {
            return cached;
        }
        final Num value = calculate(index);
        valueCache.put(index, value);
        return value;
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
        ensureCandidate(windowStart, endIndex);
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
            cachedWindowSwings = List.of();
            cachedGeometries = List.of();
            cachedRemovedBars = removedBars;
        }
    }

    private void ensureCandidate(int windowStart, int windowEnd) {
        final List<Integer> windowSwings = windowedSwings(windowStart, windowEnd,
                swingIndicator.getSwingPointIndexesUpTo(windowEnd));
        final boolean geometryStale = cachedGeometries.isEmpty() || cachedEndIndex != windowEnd
                || cachedWindowStart != windowStart || !cachedWindowSwings.equals(windowSwings);
        if (geometryStale) {
            cachedGeometries = buildGeometries(windowStart, windowEnd, windowSwings);
            cachedWindowSwings = windowSwings;
            cachedSegment = null;
            valueCache.clear();
        }
        if (cachedGeometries.isEmpty()) {
            cachedSegment = null;
            return;
        }
        cachedSegment = selectBestCandidate(windowEnd, resolvePriceAtIndex(windowEnd), getBarSeries().numFactory());
        cachedEndIndex = windowEnd;
        cachedWindowStart = windowStart;
    }

    private List<Integer> windowedSwings(int windowStart, int windowEnd, List<Integer> swingPoints) {
        final List<Integer> windowedSwings = new ArrayList<>();
        for (int idx : swingPoints) {
            if (idx >= windowStart && idx <= windowEnd) {
                final Num price = swingPriceAt(idx);
                if (isInvalid(price)) {
                    continue;
                }
                windowedSwings.add(idx);
            }
        }
        return windowedSwings;
    }

    private List<CandidateGeometry> buildGeometries(int windowStart, int windowEnd, List<Integer> swingPoints) {
        if (swingPoints.size() < 2) {
            return List.of();
        }
        final Num extremeSwingPrice = findExtremeSwingPrice(swingPoints);
        final Num swingRange = findSwingRange(swingPoints);
        if (isInvalid(extremeSwingPrice) || isInvalid(swingRange)) {
            return List.of();
        }
        refreshCoordinateBase();
        final int windowLength = windowEnd - windowStart + 1;
        final NumFactory numFactory = getBarSeries().numFactory();
        final List<CandidateGeometry> geometries = new ArrayList<>();
        for (int i = 0; i < swingPoints.size() - 1; i++) {
            final int firstSwingIndex = swingPoints.get(i);
            for (int j = i + 1; j < swingPoints.size(); j++) {
                final int secondSwingIndex = swingPoints.get(j);
                final CandidateGeometry geometry = buildCandidateGeometry(firstSwingIndex, secondSwingIndex,
                        swingPoints, windowStart, windowEnd, windowLength, extremeSwingPrice, swingRange, numFactory);
                if (geometry != null) {
                    geometries.add(geometry);
                }
            }
        }
        return geometries;
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

    private Num swingPriceAt(int index) {
        final Num price = priceIndicator.getValue(index);
        if (!isInvalid(price)) {
            return price;
        }
        if (getBarSeries() == null || index < getBarSeries().getBeginIndex() || index > getBarSeries().getEndIndex()) {
            return price;
        }
        final Num fallback = side.selectBarPrice(getBarSeries().getBar(index));
        return isInvalid(fallback) ? price : fallback;
    }

    private TrendLineCandidate selectBestCandidate(int evaluationIndex, Num priceAtEvaluation, NumFactory numFactory) {
        if (cachedGeometries.isEmpty()) {
            return null;
        }
        TrendLineCandidate bestCandidate = null;
        for (CandidateGeometry geometry : cachedGeometries) {
            final TrendLineCandidate candidate = geometry.toCandidate(priceAtEvaluation, evaluationIndex, numFactory);
            if (candidate == null) {
                continue;
            }
            if (bestCandidate == null || candidate.isBetterThan(bestCandidate, priceAtEvaluation)) {
                bestCandidate = candidate;
            }
        }
        return bestCandidate;
    }

    private CandidateGeometry buildCandidateGeometry(int firstSwingIndex, int secondSwingIndex,
            List<Integer> swingPointIndexes, int windowStart, int windowEnd, int windowLength, Num extremeSwingPrice,
            Num swingRange, NumFactory numFactory) {
        final Num firstValue = swingPriceAt(firstSwingIndex);
        final Num secondValue = swingPriceAt(secondSwingIndex);
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

        int touchingSwingCount = 0;
        int outsideSwingCount = 0;
        boolean touchesExtremeSwing = false;
        double totalDeviation = 0d;
        for (int swingIndex : swingPointIndexes) {
            final Num swingPrice = swingPriceAt(swingIndex);
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
                touchingSwingCount++;
                if (!isInvalid(extremeSwingPrice) && swingPrice.isEqual(extremeSwingPrice)) {
                    touchesExtremeSwing = true;
                }
            } else {
                outsideSwingCount++;
                if (side.violates(projectedAtSwing, swingPrice)) {
                    return null;
                }
            }
            final double deviationValue = Math.abs(projectedAtSwing.minus(swingPrice).doubleValue());
            totalDeviation += deviationValue;
        }

        final int mostRecentAnchor = Math.max(firstSwingIndex, secondSwingIndex);
        final double recencyAnchorScore = Math.min(1d,
                Math.max(0d, (double) (mostRecentAnchor - windowStart) / windowLength));
        final double baseScore = calculateBaseScore(touchingSwingCount, swingPointIndexes.size(), touchesExtremeSwing,
                outsideSwingCount, totalDeviation, swingRange.doubleValue(), recencyAnchorScore);

        return new CandidateGeometry(firstSwingIndex, secondSwingIndex, slope, intercept, touchingSwingCount,
                outsideSwingCount, touchesExtremeSwing, baseScore, windowStart, windowEnd);
    }

    private boolean isInvalid(Num value) {
        return value == null || value.isNaN() || Double.isNaN(value.doubleValue());
    }

    private Num findSwingRange(List<Integer> swingPointIndexes) {
        Num min = null;
        Num max = null;
        for (int swingIndex : swingPointIndexes) {
            final Num swingPrice = swingPriceAt(swingIndex);
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
        return toleranceSettings.toleranceFor(swingRange, numFactory);
    }

    private Num findExtremeSwingPrice(List<Integer> swingPointIndexes) {
        Num extreme = null;
        for (int swingIndex : swingPointIndexes) {
            final Num swingPrice = swingPriceAt(swingIndex);
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

    private double calculateBaseScore(int touchingSwingCount, int totalSwings, boolean touchesExtremeSwing,
            int outsideSwingCount, double totalDeviation, double swingRange, double recencyAnchorScore) {
        if (totalSwings <= 0) {
            return 0d;
        }
        final double touchScore = (double) touchingSwingCount / (double) totalSwings;
        final double extremeScore = touchesExtremeSwing ? 1d : 0d;
        final double outsidePenalty = Math.min(outsideSwingCount, totalSwings);
        final double outsideScore = 1d - (outsidePenalty / (double) totalSwings);
        final double averageDeviation = totalDeviation / (double) totalSwings;
        final boolean invalidRange = Double.isNaN(swingRange) || swingRange <= 0d;
        final double normalizedDeviation = invalidRange ? 0d : Math.min(1d, averageDeviation / swingRange);
        final double proximityScore = 1d - normalizedDeviation;
        return touchWeight * touchScore + extremeWeight * extremeScore + outsideWeight * outsideScore
                + proximityWeight * proximityScore + recencyWeight * recencyAnchorScore;
    }

    private final class TrendLineCandidate {
        private final int firstIndex;
        private final int secondIndex;
        private final Num slope;
        private final Num intercept;
        private final int touchingSwingCount;
        private final int outsideSwingCount;
        private final boolean touchesExtremeSwing;
        private final double score;
        private final int windowStart;
        private final int windowEnd;

        private TrendLineCandidate(int firstIndex, int secondIndex, Num slope, Num intercept, int touchingSwingCount,
                int outsideSwingCount, boolean touchesExtremeSwing, double score, int windowStart, int windowEnd) {
            this.firstIndex = firstIndex;
            this.secondIndex = secondIndex;
            this.slope = slope;
            this.intercept = intercept;
            this.touchingSwingCount = touchingSwingCount;
            this.outsideSwingCount = outsideSwingCount;
            this.touchesExtremeSwing = touchesExtremeSwing;
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
            if (this.touchingSwingCount != other.touchingSwingCount) {
                return this.touchingSwingCount > other.touchingSwingCount;
            }
            if (this.outsideSwingCount != other.outsideSwingCount) {
                return this.outsideSwingCount < other.outsideSwingCount;
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

    private final class CandidateGeometry {
        private final int firstIndex;
        private final int secondIndex;
        private final Num slope;
        private final Num intercept;
        private final int touchingSwingCount;
        private final int outsideSwingCount;
        private final boolean touchesExtremeSwing;
        private final double baseScore;
        private final int windowStart;
        private final int windowEnd;

        private CandidateGeometry(int firstIndex, int secondIndex, Num slope, Num intercept, int touchingSwingCount,
                int outsideSwingCount, boolean touchesExtremeSwing, double baseScore, int windowStart, int windowEnd) {
            this.firstIndex = firstIndex;
            this.secondIndex = secondIndex;
            this.slope = slope;
            this.intercept = intercept;
            this.touchingSwingCount = touchingSwingCount;
            this.outsideSwingCount = outsideSwingCount;
            this.touchesExtremeSwing = touchesExtremeSwing;
            this.baseScore = baseScore;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
        }

        private TrendLineCandidate toCandidate(Num priceAtEvaluation, int evaluationIndex, NumFactory numFactory) {
            final Num projected = slope.multipliedBy(coordinateForIndex(evaluationIndex, numFactory)).plus(intercept);
            if (isInvalid(projected)) {
                return null;
            }
            return new TrendLineCandidate(firstIndex, secondIndex, slope, intercept, touchingSwingCount,
                    outsideSwingCount, touchesExtremeSwing, baseScore, windowStart, windowEnd);
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

    public synchronized TrendLineSegment getCurrentSegment() {
        if (getBarSeries() == null) {
            return null;
        }
        refreshCachedState();
        final int beginIndex = getBarSeries().getBeginIndex();
        final int endIndex = getBarSeries().getEndIndex();
        final int windowStart = Math.max(beginIndex, endIndex - barCount + 1);
        ensureCandidate(windowStart, endIndex);
        if (cachedSegment == null) {
            return null;
        }
        return new TrendLineSegment(cachedSegment.firstIndex, cachedSegment.secondIndex, cachedSegment.slope,
                cachedSegment.intercept, cachedSegment.touchingSwingCount, cachedSegment.outsideSwingCount,
                cachedSegment.touchesExtremeSwing, false, cachedSegment.score, cachedSegment.windowStart,
                cachedSegment.windowEnd);
    }

    @Override
    public ComponentDescriptor toDescriptor() {
        final Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("barCount", barCount);
        parameters.put("unstableBars", getCountOfUnstableBars());
        parameters.put("touchWeight", touchWeight);
        parameters.put("extremeWeight", extremeWeight);
        parameters.put("outsideWeight", outsideWeight);
        parameters.put("proximityWeight", proximityWeight);
        parameters.put("recencyWeight", recencyWeight);
        parameters.put("toleranceModeOrdinal", toleranceSettings.mode.ordinal());
        parameters.put("toleranceValue", toleranceSettings.value);
        parameters.put("toleranceMinimum", toleranceSettings.minimumAbsolute);
        final ComponentDescriptor swingDescriptor = swingIndicator.toDescriptor();

        return ComponentDescriptor.builder()
                .withType(getClass().getSimpleName())
                .withParameters(parameters)
                .addComponent(swingDescriptor)
                .build();
    }

    @Override
    public String toJson() {
        return ComponentSerialization.toJson(toDescriptor());
    }

    private static ScoringWeights resolve(ScoringWeights scoringWeights) {
        return scoringWeights == null ? ScoringWeights.defaultWeights() : scoringWeights;
    }

    public static final class TrendLineSegment {
        public final int firstIndex;
        public final int secondIndex;
        public final Num slope;
        public final Num intercept;
        public final int touches;
        public final int outsideCount;
        public final boolean touchesExtreme;
        public final double score;
        public final int windowStart;
        public final int windowEnd;

        private TrendLineSegment(int firstIndex, int secondIndex, Num slope, Num intercept, int touches,
                int outsideCount, boolean touchesExtreme, boolean containsCurrentPrice, double score, int windowStart,
                int windowEnd) {
            this.firstIndex = firstIndex;
            this.secondIndex = secondIndex;
            this.slope = slope;
            this.intercept = intercept;
            this.touches = touches;
            this.outsideCount = outsideCount;
            this.touchesExtreme = touchesExtreme;
            this.score = score;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
        }
    }

    public static final class ToleranceSettings {
        public enum Mode {
            PERCENTAGE, ABSOLUTE, TICK_SIZE
        }

        public final Mode mode;
        public final double value;
        public final double minimumAbsolute;

        private ToleranceSettings(Mode mode, double value, double minimumAbsolute) {
            this.mode = mode;
            this.value = value;
            this.minimumAbsolute = Math.max(0d, minimumAbsolute);
        }

        public static ToleranceSettings defaultSettings() {
            return percentage(0.02d, 1e-9d);
        }

        public static ToleranceSettings percentage(double fraction, double minimumAbsolute) {
            return new ToleranceSettings(Mode.PERCENTAGE, fraction, minimumAbsolute);
        }

        public static ToleranceSettings absolute(double absoluteTolerance) {
            return new ToleranceSettings(Mode.ABSOLUTE, absoluteTolerance, 0d);
        }

        public static ToleranceSettings tickSize(double tickSize) {
            return new ToleranceSettings(Mode.TICK_SIZE, tickSize, 0d);
        }

        public static ToleranceSettings from(String mode, double value, double minimumAbsolute) {
            final Mode parsedMode = Mode.valueOf(mode);
            return new ToleranceSettings(parsedMode, value, minimumAbsolute);
        }

        public static ToleranceSettings from(Mode mode, double value, double minimumAbsolute) {
            return new ToleranceSettings(mode, value, minimumAbsolute);
        }

        private Num toleranceFor(Num swingRange, NumFactory numFactory) {
            switch (mode) {
            case ABSOLUTE:
                return sanitize(numFactory.numOf(value), numFactory);
            case TICK_SIZE:
                return sanitize(numFactory.numOf(value), numFactory);
            case PERCENTAGE:
            default:
                if (swingRange == null || swingRange.isNaN()) {
                    return NaN;
                }
                final Num fraction = numFactory.numOf(value);
                final Num tolerance = swingRange.multipliedBy(fraction);
                return sanitize(tolerance, numFactory);
            }
        }

        private Num sanitize(Num tolerance, NumFactory numFactory) {
            final Num minimum = numFactory.numOf(minimumAbsolute);
            if (tolerance.isNaN() || tolerance.isZero()) {
                return minimum;
            }
            return tolerance.isLessThan(minimum) ? minimum : tolerance;
        }
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

        private ScoringWeights(double touchWeight, double extremeWeight, double outsideWeight, double proximityWeight,
                double recencyWeight) {
            this.touchWeight = touchWeight;
            this.extremeWeight = extremeWeight;
            this.outsideWeight = outsideWeight;
            this.proximityWeight = proximityWeight;
            this.recencyWeight = recencyWeight;
            validateWeights();
        }

        public static ScoringWeights defaultWeights() {
            return new ScoringWeights(0.30d, 0.20d, 0.15d, 0.20d, 0.15d);
        }

        /**
         * Creates scoring weights from explicit fractional percentages.
         */
        public static ScoringWeights of(double touchWeight, double extremeWeight, double outsideWeight,
                double proximityWeight, double recencyWeight) {
            return new ScoringWeights(touchWeight, extremeWeight, outsideWeight, proximityWeight, recencyWeight);
        }

        /**
         * Heavier emphasis on touching swing points with moderate proximity/outside
         * penalties.
         */
        public static ScoringWeights touchHeavyPreset() {
            return new ScoringWeights(0.50d, 0.15d, 0.10d, 0.10d, 0.15d);
        }

        /**
         * Heavier emphasis on touching the extreme swing while keeping lines reasonably
         * close to price action.
         */
        public static ScoringWeights extremeHeavyPreset() {
            return new ScoringWeights(0.35d, 0.35d, 0.10d, 0.10d, 0.10d);
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
            private double recencyWeight = 0.15d;

            public Builder weightForTouchingSwingPoints(double weightFraction) {
                this.touchWeight = weightFraction;
                return this;
            }

            public Builder weightForTouchingExtremeSwing(double weightFraction) {
                this.extremeWeight = weightFraction;
                return this;
            }

            public Builder weightForKeepingSwingsInsideLine(double weightFraction) {
                this.outsideWeight = weightFraction;
                return this;
            }

            public Builder weightForStayingCloseToSwings(double weightFraction) {
                this.proximityWeight = weightFraction;
                return this;
            }

            public Builder weightForRecentAnchorPoints(double weightFraction) {
                this.recencyWeight = weightFraction;
                return this;
            }

            public ScoringWeights build() {
                return new ScoringWeights(touchWeight, extremeWeight, outsideWeight, proximityWeight, recencyWeight);
            }
        }

        private void validateWeights() {
            validateFraction(touchWeight, "touchWeight");
            validateFraction(extremeWeight, "extremeWeight");
            validateFraction(outsideWeight, "outsideWeight");
            validateFraction(proximityWeight, "proximityWeight");
            validateFraction(recencyWeight, "recencyWeight");
            final double sum = touchWeight + extremeWeight + outsideWeight + proximityWeight + recencyWeight;
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
