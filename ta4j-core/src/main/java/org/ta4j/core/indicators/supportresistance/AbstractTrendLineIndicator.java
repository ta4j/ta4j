/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.supportresistance;

import org.ta4j.core.Bar;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

import java.util.*;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Abstract base for trend line indicators that rely on previously confirmed
 * swing highs or lows.
 * <p>
 * The indicator computes the best trend line for the latest window of
 * {@code barCount} bars anchored at the series end. Bars that fall outside the
 * look-back window return {@code NaN}. Bars inside the window (including the
 * {@code windowStart}) return the active line when at least two swing points
 * exist; otherwise, they return {@code NaN}. When a new bar arrives, the
 * current trend line is recomputed for the new window. The current segment and
 * tolerance settings can be inspected via {@link #getCurrentSegment()} and
 * {@link #getToleranceSettings()}. By default, searches are capped to the most
 * recent {@value #DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE} swing points and
 * {@value #DEFAULT_MAX_CANDIDATE_PAIRS} candidate pairs; use the extended
 * constructors to widen the search if needed.
 *
 * @since 0.20
 */
public abstract class AbstractTrendLineIndicator extends CachedIndicator<Num> {

    /**
     * Default maximum number of swing points to consider when building trend line
     * candidates. This cap prevents excessive computation when many swing points
     * exist in the look-back window. Only the most recent swing points up to this
     * limit are used.
     */
    public static final int DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE = 64;

    /**
     * Default maximum number of candidate trend line pairs to evaluate. This cap
     * prevents excessive computation when many swing point pairs exist. The search
     * stops once this limit is reached, prioritizing earlier pairs in the
     * evaluation order.
     */
    public static final int DEFAULT_MAX_CANDIDATE_PAIRS = 2048;

    private final RecentSwingIndicator swingIndicator;
    private final transient Indicator<Num> priceIndicator;
    private final int barCount;
    private final TrendLineSide side;
    private final ScoringWeights scoringWeights;
    private final ToleranceSettings toleranceSettings;
    private final int maxSwingPointsForTrendline;
    private final int maxCandidatePairs;

    private transient TrendLineCandidate cachedSegment;
    private transient int cachedEndIndex = Integer.MIN_VALUE;
    private transient int cachedWindowStart = Integer.MIN_VALUE;
    private transient int cachedRemovedBars = Integer.MIN_VALUE;
    private transient List<Integer> cachedWindowSwings = List.of();
    private transient List<TrendLineCandidate> cachedGeometries = List.of();
    private transient long coordinateBaseEpochMillis = Long.MIN_VALUE;
    private transient int coordinateBaseIndex = Integer.MIN_VALUE;

    protected AbstractTrendLineIndicator(RecentSwingIndicator swingIndicator, int barCount, TrendLineSide side,
            ScoringWeights scoringWeights) {
        this(swingIndicator, barCount, side, resolve(scoringWeights).touchCountWeight,
                resolve(scoringWeights).touchesExtremeWeight, resolve(scoringWeights).outsideCountWeight,
                resolve(scoringWeights).averageDeviationWeight, resolve(scoringWeights).anchorRecencyWeight,
                ToleranceSettings.defaultSettings());
    }

    protected AbstractTrendLineIndicator(RecentSwingIndicator swingIndicator, int barCount, TrendLineSide side,
            double touchCountWeight, double touchesExtremeWeight, double outsideCountWeight,
            double averageDeviationWeight, double anchorRecencyWeight, ToleranceSettings toleranceSettings) {
        this(swingIndicator, barCount, side, touchCountWeight, touchesExtremeWeight, outsideCountWeight,
                averageDeviationWeight, anchorRecencyWeight, toleranceSettings, DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE,
                DEFAULT_MAX_CANDIDATE_PAIRS);
    }

    protected AbstractTrendLineIndicator(RecentSwingIndicator swingIndicator, int barCount, TrendLineSide side,
            double touchCountWeight, double touchesExtremeWeight, double outsideCountWeight,
            double averageDeviationWeight, double anchorRecencyWeight, ToleranceSettings toleranceSettings,
            int maxSwingPointsForTrendline, int maxCandidatePairs) {
        super(swingIndicator.getPriceIndicator());
        if (barCount < 2) {
            throw new IllegalArgumentException("barCount must be at least 2 to build a trend line");
        }
        if (maxSwingPointsForTrendline < 2) {
            throw new IllegalArgumentException("maxSwingPointsForTrendline must be at least 2");
        }
        if (maxCandidatePairs < 1) {
            throw new IllegalArgumentException("maxCandidatePairs must be at least 1");
        }
        this.swingIndicator = swingIndicator;
        this.priceIndicator = swingIndicator.getPriceIndicator();
        this.barCount = barCount;
        this.side = side;
        this.scoringWeights = new ScoringWeights(touchCountWeight, touchesExtremeWeight, outsideCountWeight,
                averageDeviationWeight, anchorRecencyWeight);
        this.toleranceSettings = toleranceSettings == null ? ToleranceSettings.defaultSettings() : toleranceSettings;
        this.maxSwingPointsForTrendline = maxSwingPointsForTrendline;
        this.maxCandidatePairs = maxCandidatePairs;
    }

    protected AbstractTrendLineIndicator(RecentSwingIndicator swingIndicator, TrendLineSide side,
            ScoringWeights scoringWeights) {
        this(swingIndicator, swingIndicator.getBarSeries().getMaximumBarCount(), side, scoringWeights);
    }

    @Override
    public int getCountOfUnstableBars() {
        return swingIndicator.getCountOfUnstableBars();
    }

    @Override
    public synchronized Num getValue(int index) {
        refreshCachedState();
        final int beginIndex = getBarSeries().getBeginIndex();
        final int endIndex = getBarSeries().getEndIndex();
        if (index < beginIndex || index > endIndex) {
            return NaN;
        }
        final int windowStart = Math.max(beginIndex, endIndex - barCount + 1);
        if (index < windowStart) {
            return NaN;
        }
        ensureGeometries(windowStart, endIndex);
        ensureCandidate(windowStart, endIndex);
        return super.getValue(index);
    }

    @Override
    protected Num calculate(int index) {
        if (cachedSegment == null) {
            return NaN;
        }
        return cachedSegment.valueAt(index);
    }

    private void refreshCachedState() {
        final int endIndex = getBarSeries().getEndIndex();
        final int removedBars = getBarSeries().getRemovedBarsCount();
        if (endIndex != cachedEndIndex || removedBars != cachedRemovedBars) {
            invalidateCache();
            cachedSegment = null;
            cachedWindowStart = Integer.MIN_VALUE;
            cachedEndIndex = Integer.MIN_VALUE;
            cachedWindowSwings = List.of();
            cachedGeometries = List.of();
            cachedRemovedBars = removedBars;
            cachedEndIndex = endIndex;
        }
    }

    private void ensureGeometries(int windowStart, int windowEnd) {
        final List<Integer> windowSwings = windowedSwings(windowStart, windowEnd,
                swingIndicator.getSwingPointIndexesUpTo(windowEnd));
        final boolean geometryStale = cachedGeometries.isEmpty() || cachedEndIndex != windowEnd
                || cachedWindowStart != windowStart || !cachedWindowSwings.equals(windowSwings);
        if (!geometryStale) {
            return;
        }
        invalidateFrom(windowStart);
        cachedGeometries = buildGeometries(windowStart, windowEnd, windowSwings);
        cachedWindowSwings = windowSwings;
        cachedSegment = null;
    }

    private void ensureCandidate(int windowStart, int windowEnd) {
        if (cachedGeometries.isEmpty()) {
            cachedSegment = null;
            return;
        }
        cachedSegment = selectBestCandidate(windowEnd, resolvePriceAtIndex(windowEnd));
        cachedEndIndex = windowEnd;
        cachedWindowStart = windowStart;
    }

    private List<Integer> windowedSwings(int windowStart, int windowEnd, List<Integer> swingPoints) {
        final List<Integer> windowedSwings = new ArrayList<>();
        for (int idx : swingPoints) {
            if (idx >= windowStart && idx <= windowEnd) {
                final Num price = swingPriceAt(idx);
                if (Num.isNaNOrNull(price)) {
                    continue;
                }
                windowedSwings.add(idx);
            }
        }
        if (windowedSwings.size() <= maxSwingPointsForTrendline) {
            return windowedSwings;
        }
        return windowedSwings.subList(windowedSwings.size() - maxSwingPointsForTrendline, windowedSwings.size());
    }

    private List<TrendLineCandidate> buildGeometries(int windowStart, int windowEnd, List<Integer> swingPoints) {
        if (swingPoints.size() < 2) {
            return List.of();
        }
        final Num extremeSwingPrice = findExtremeSwingPrice(swingPoints);
        final Num swingRange = findSwingRange(swingPoints);
        if (Num.isNaNOrNull(extremeSwingPrice) || Num.isNaNOrNull(swingRange)) {
            return List.of();
        }
        refreshCoordinateBase();
        final int windowLength = windowEnd - windowStart + 1;
        final List<TrendLineCandidate> geometries = new ArrayList<>();
        for (int i = 0; i < swingPoints.size() - 1; i++) {
            final int firstSwingIndex = swingPoints.get(i);
            for (int j = i + 1; j < swingPoints.size(); j++) {
                final int secondSwingIndex = swingPoints.get(j);
                final TrendLineCandidate geometry = buildCandidateGeometry(firstSwingIndex, secondSwingIndex,
                        swingPoints, windowStart, windowEnd, windowLength, extremeSwingPrice, swingRange);
                if (geometry != null) {
                    geometries.add(geometry);
                    if (geometries.size() >= maxCandidatePairs) {
                        return geometries;
                    }
                }
            }
        }
        return geometries;
    }

    /**
     * Returns the indexes of all confirmed swing points tracked by the underlying
     * swing indicator. These are the swing points that have been confirmed (not
     * just candidate swings) and are available for trend line construction.
     *
     * @return a list of bar indexes where confirmed swing points occur, in
     *         chronological order
     */
    public List<Integer> getSwingPointIndexes() {
        return swingIndicator.getSwingPointIndexes();
    }

    private Num resolvePriceAtIndex(int index) {
        final Num price = priceIndicator.getValue(index);
        if (!Num.isNaNOrNull(price)) {
            return price;
        }
        if (getBarSeries() == null || index < getBarSeries().getBeginIndex() || index > getBarSeries().getEndIndex()) {
            return price;
        }
        return side.selectBarPrice(getBarSeries().getBar(index));
    }

    private Num swingPriceAt(int index) {
        final Num price = priceIndicator.getValue(index);
        if (!Num.isNaNOrNull(price)) {
            return price;
        }
        if (getBarSeries() == null || index < getBarSeries().getBeginIndex() || index > getBarSeries().getEndIndex()) {
            return price;
        }
        final Num fallback = side.selectBarPrice(getBarSeries().getBar(index));
        return Num.isNaNOrNull(fallback) ? price : fallback;
    }

    private TrendLineCandidate selectBestCandidate(int evaluationIndex, Num priceAtEvaluation) {
        if (cachedGeometries.isEmpty()) {
            return null;
        }
        TrendLineCandidate bestCandidate = null;
        for (TrendLineCandidate candidate : cachedGeometries) {
            final Num projected = candidate.valueAt(evaluationIndex);
            if (Num.isNaNOrNull(projected)) {
                continue;
            }
            if (bestCandidate == null || candidate.isBetterThan(bestCandidate, priceAtEvaluation)) {
                bestCandidate = candidate;
            }
        }
        return bestCandidate;
    }

    private TrendLineCandidate buildCandidateGeometry(int firstSwingIndex, int secondSwingIndex,
            List<Integer> swingPointIndexes, int windowStart, int windowEnd, int windowLength, Num extremeSwingPrice,
            Num swingRange) {
        final Num firstValue = swingPriceAt(firstSwingIndex);
        final Num secondValue = swingPriceAt(secondSwingIndex);
        if (Num.isNaNOrNull(firstValue) || Num.isNaNOrNull(secondValue)) {
            return null;
        }
        final NumFactory numFactory = getBarSeries().numFactory();
        final Num x1 = coordinateForIndex(firstSwingIndex);
        final Num x2 = coordinateForIndex(secondSwingIndex);
        final Num denominator = x2.minus(x1);
        if (Num.isNaNOrNull(denominator) || denominator.isZero()) {
            return null;
        }
        final Num slope = secondValue.minus(firstValue).dividedBy(denominator);
        if (Num.isNaNOrNull(slope)) {
            return null;
        }
        final Num intercept = firstValue.minus(slope.multipliedBy(x1));
        if (Num.isNaNOrNull(intercept)) {
            return null;
        }

        int touchCount = 0;
        int outsideCount = 0;
        boolean touchesExtreme = false;
        Num totalDeviation = numFactory.zero();
        for (int swingIndex : swingPointIndexes) {
            final Num swingPrice = swingPriceAt(swingIndex);
            if (Num.isNaNOrNull(swingPrice)) {
                return null;
            }
            final Num projectedAtSwing = slope.multipliedBy(coordinateForIndex(swingIndex)).plus(intercept);
            if (Num.isNaNOrNull(projectedAtSwing)) {
                return null;
            }
            final Num tolerance = toleranceForSwing(swingRange, numFactory);
            final boolean isAnchor = swingIndex == firstSwingIndex || swingIndex == secondSwingIndex;
            final Num deviation = projectedAtSwing.minus(swingPrice).abs();
            final boolean touchesSwing = isAnchor
                    || (!Num.isNaNOrNull(tolerance) && (deviation.isZero() || deviation.isLessThanOrEqual(tolerance)));
            if (touchesSwing) {
                touchCount++;
                if (!Num.isNaNOrNull(extremeSwingPrice) && swingPrice.isEqual(extremeSwingPrice)) {
                    touchesExtreme = true;
                }
            } else {
                outsideCount++;
                if (side.violates(projectedAtSwing, swingPrice)) {
                    return null;
                }
            }
            totalDeviation = totalDeviation.plus(deviation);
        }

        final int mostRecentAnchor = Math.max(firstSwingIndex, secondSwingIndex);
        final double recencyAnchorScore = Math.min(1d,
                Math.max(0d, (double) (mostRecentAnchor - windowStart) / windowLength));
        final double baseScore = calculateBaseScore(touchCount, swingPointIndexes.size(), touchesExtreme, outsideCount,
                totalDeviation.doubleValue(), swingRange.doubleValue(), recencyAnchorScore);

        return new TrendLineCandidate(firstSwingIndex, secondSwingIndex, slope, intercept, touchCount, outsideCount,
                touchesExtreme, baseScore, windowStart, windowEnd);
    }

    private Num findSwingRange(List<Integer> swingPointIndexes) {
        Num min = null;
        Num max = null;
        for (int swingIndex : swingPointIndexes) {
            final Num swingPrice = swingPriceAt(swingIndex);
            if (Num.isNaNOrNull(swingPrice)) {
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
            if (Num.isNaNOrNull(swingPrice)) {
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

    private double calculateBaseScore(int touchCount, int totalSwingAnchorPoints, boolean touchesExtreme,
            int outsideCount, double totalSwingDeviation, double swingRange, double recencyAnchorScore) {
        if (totalSwingAnchorPoints <= 0) {
            return 0d;
        }
        final double touchScore = (double) touchCount / (double) totalSwingAnchorPoints;
        final double extremeScore = touchesExtreme ? 1d : 0d;
        final double outsidePenalty = Math.min(outsideCount, totalSwingAnchorPoints);
        final double outsideScore = 1d - (outsidePenalty / (double) totalSwingAnchorPoints);
        final double averageDeviation = totalSwingDeviation / (double) totalSwingAnchorPoints;
        final boolean invalidRange = Double.isNaN(swingRange) || swingRange <= 0d;
        final double normalizedDeviation = invalidRange ? 0d : Math.min(1d, averageDeviation / swingRange);
        final double proximityScore = 1d - normalizedDeviation;
        return scoringWeights.touchCountWeight * touchScore + scoringWeights.touchesExtremeWeight * extremeScore
                + scoringWeights.outsideCountWeight * outsideScore
                + scoringWeights.averageDeviationWeight * proximityScore
                + scoringWeights.anchorRecencyWeight * recencyAnchorScore;
    }

    private final class TrendLineCandidate {
        private final int firstIndex;
        private final int secondIndex;
        private final Num slope;
        private final Num intercept;
        private final int touchCount;
        private final int outsideCount;
        private final boolean touchesExtreme;
        private final double score;
        private final int windowStart;
        private final int windowEnd;

        private TrendLineCandidate(int firstIndex, int secondIndex, Num slope, Num intercept, int touchCount,
                int outsideCount, boolean touchesExtreme, double score, int windowStart, int windowEnd) {
            this.firstIndex = firstIndex;
            this.secondIndex = secondIndex;
            this.slope = slope;
            this.intercept = intercept;
            this.touchCount = touchCount;
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
            if (this.touchCount != other.touchCount) {
                return this.touchCount > other.touchCount;
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
                final Num thisValue = valueAt(windowEnd);
                final Num otherValue = other.valueAt(other.windowEnd);
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

        private Num valueAt(int index) {
            final Num coordinate = coordinateForIndex(index);
            return slope.multipliedBy(coordinate).plus(intercept);
        }
    }

    /**
     * Enumeration of trend line sides, determining whether the indicator projects
     * support (below price) or resistance (above price) trend lines.
     */
    protected enum TrendLineSide {
        /**
         * Support trend lines that run below price action, connecting swing lows. The
         * trend line must not violate (go above) any swing low prices.
         */
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
        /**
         * Resistance trend lines that run above price action, connecting swing highs.
         * The trend line must not violate (go below) any swing high prices.
         */
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

        abstract Num selectBarPrice(Bar bar);
    }

    /**
     * Returns the scoring weights used to evaluate trend line candidates. These
     * weights determine how different factors (touch count, extreme point
     * inclusion, outside swings, deviation, recency) contribute to the overall
     * score.
     *
     * @return the scoring weights configuration, never null
     */
    public ScoringWeights getScoringWeights() {
        return scoringWeights;
    }

    /**
     * Returns the tolerance settings that determine how close a swing point must be
     * to a trend line to be considered "touching" it. Tolerance can be specified as
     * a percentage of swing range, an absolute value, or in tick sizes.
     *
     * @return the tolerance settings, never null
     */
    public ToleranceSettings getToleranceSettings() {
        return toleranceSettings;
    }

    /**
     * Returns the maximum number of swing points considered when building trend
     * line candidates. Only the most recent swing points up to this limit are used.
     *
     * @return the maximum number of swing points to consider
     */
    public int getMaxSwingPointsForTrendline() {
        return maxSwingPointsForTrendline;
    }

    /**
     * Returns the maximum number of candidate trend line pairs to evaluate. The
     * search stops once this limit is reached.
     *
     * @return the maximum number of candidate pairs to evaluate
     */
    public int getMaxCandidatePairs() {
        return maxCandidatePairs;
    }

    /**
     * Returns metadata about the currently selected trend line segment for the
     * latest window. This includes the anchor points, line equation (slope and
     * intercept), scoring metrics, and the window boundaries. Useful for charting,
     * diagnostics, and understanding why a particular trend line was selected.
     * <p>
     * The segment is computed lazily and cached. It is recalculated when new bars
     * arrive or when the series is modified.
     *
     * @return the current trend line segment, or {@code null} if no valid trend
     *         line could be computed (e.g., fewer than two swing points exist)
     */
    public synchronized TrendLineSegment getCurrentSegment() {
        if (getBarSeries() == null) {
            return null;
        }
        refreshCachedState();
        final int beginIndex = getBarSeries().getBeginIndex();
        final int endIndex = getBarSeries().getEndIndex();
        final int windowStart = Math.max(beginIndex, endIndex - barCount + 1);
        ensureGeometries(windowStart, endIndex);
        ensureCandidate(windowStart, endIndex);
        if (cachedSegment == null) {
            return null;
        }
        return new TrendLineSegment(cachedSegment.firstIndex, cachedSegment.secondIndex, cachedSegment.slope,
                cachedSegment.intercept, cachedSegment.touchCount, cachedSegment.outsideCount,
                cachedSegment.touchesExtreme, cachedSegment.score, cachedSegment.windowStart, cachedSegment.windowEnd);
    }

    @Override
    public ComponentDescriptor toDescriptor() {
        final Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("barCount", barCount);
        parameters.put("touchCountWeight", scoringWeights.touchCountWeight);
        parameters.put("touchesExtremeWeight", scoringWeights.touchesExtremeWeight);
        parameters.put("outsideCountWeight", scoringWeights.outsideCountWeight);
        parameters.put("averageDeviationWeight", scoringWeights.averageDeviationWeight);
        parameters.put("anchorRecencyWeight", scoringWeights.anchorRecencyWeight);
        parameters.put("maxSwingPointsForTrendline", maxSwingPointsForTrendline);
        parameters.put("maxCandidatePairs", maxCandidatePairs);
        parameters.put("toleranceMode", toleranceSettings.mode.name());
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

    /**
     * Metadata about a selected trend line segment, including its geometry, scoring
     * metrics, and window boundaries. This class provides read-only access to the
     * trend line's properties for charting and analysis purposes.
     */
    public static final class TrendLineSegment {
        /** The bar index of the first anchor point (earlier in time). */
        public final int firstIndex;

        /** The bar index of the second anchor point (later in time). */
        public final int secondIndex;

        /** The slope of the trend line (price change per unit time). */
        public final Num slope;

        /** The y-intercept of the trend line at the coordinate base. */
        public final Num intercept;

        /**
         * The number of swing points that touch or are within tolerance of the trend
         * line. Higher values indicate better fit.
         */
        public final int touchCount;

        /**
         * The number of swing points that fall outside the trend line (but do not
         * violate it). Lower values indicate better fit.
         */
        public final int outsideCount;

        /**
         * Whether the trend line touches the extreme swing point (lowest for support,
         * highest for resistance). This is a positive scoring factor.
         */
        public final boolean touchesExtreme;

        /**
         * The composite score for this trend line candidate, computed from the weighted
         * combination of all scoring factors. Higher scores indicate better candidates.
         */
        public final double score;

        /** The first bar index in the evaluation window. */
        public final int windowStart;

        /** The last bar index in the evaluation window (typically the series end). */
        public final int windowEnd;

        private TrendLineSegment(int firstIndex, int secondIndex, Num slope, Num intercept, int touchCount,
                int outsideCount, boolean touchesExtreme, double score, int windowStart, int windowEnd) {
            this.firstIndex = firstIndex;
            this.secondIndex = secondIndex;
            this.slope = slope;
            this.intercept = intercept;
            this.touchCount = touchCount;
            this.outsideCount = outsideCount;
            this.touchesExtreme = touchesExtreme;
            this.score = score;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
        }
    }

    /**
     * Configuration for determining how close a swing point must be to a trend line
     * to be considered "touching" it. Tolerance can be specified in three modes: as
     * a percentage of the swing price range, as an absolute price value, or in tick
     * sizes.
     */
    public static final class ToleranceSettings {
        /**
         * Tolerance calculation modes.
         */
        public enum Mode {
            /**
             * Tolerance is calculated as a percentage of the swing price range (max - min)
             * within the evaluation window.
             */
            PERCENTAGE,

            /**
             * Tolerance is a fixed absolute price value, independent of price range.
             */
            ABSOLUTE,

            /**
             * Tolerance is specified in tick sizes, useful for instruments with fixed tick
             * increments.
             */
            TICK_SIZE
        }

        /** The tolerance calculation mode. */
        public final Mode mode;

        /**
         * The tolerance value. Interpretation depends on mode: percentage (0.0-1.0),
         * absolute price, or tick size.
         */
        public final double value;

        /**
         * The minimum absolute tolerance value to apply, regardless of mode. This
         * prevents tolerance from becoming too small for very tight price ranges.
         */
        public final double minimumAbsolute;

        private ToleranceSettings(Mode mode, double value, double minimumAbsolute) {
            this.mode = mode;
            validateValue(mode, value);
            validateMinimum(minimumAbsolute);
            this.value = value;
            this.minimumAbsolute = Math.max(0d, minimumAbsolute);
        }

        /**
         * Returns default tolerance settings: 2% of swing range with a minimum absolute
         * tolerance of 1e-9. This provides a good balance between allowing reasonable
         * price variation and maintaining trend line quality.
         *
         * @return default tolerance settings
         */
        public static ToleranceSettings defaultSettings() {
            return percentage(0.02d, 1e-9d);
        }

        /**
         * Creates tolerance settings where tolerance is calculated as a percentage of
         * the swing price range.
         *
         * @param fraction        the tolerance as a fraction of swing range (e.g., 0.02
         *                        for 2%)
         * @param minimumAbsolute the minimum absolute tolerance to apply
         * @return tolerance settings with percentage mode
         */
        public static ToleranceSettings percentage(double fraction, double minimumAbsolute) {
            return new ToleranceSettings(Mode.PERCENTAGE, fraction, minimumAbsolute);
        }

        /**
         * Creates tolerance settings with a fixed absolute price tolerance.
         *
         * @param absoluteTolerance the absolute price tolerance
         * @return tolerance settings with absolute mode
         */
        public static ToleranceSettings absolute(double absoluteTolerance) {
            return new ToleranceSettings(Mode.ABSOLUTE, absoluteTolerance, 0d);
        }

        /**
         * Creates tolerance settings where tolerance is specified in tick sizes.
         *
         * @param tickSize the tolerance in tick sizes
         * @return tolerance settings with tick size mode
         */
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

        private void validateValue(Mode mode, double candidateValue) {
            if (Double.isNaN(candidateValue) || Double.isInfinite(candidateValue)) {
                throw new IllegalArgumentException("Tolerance value must be finite");
            }
            if (candidateValue < 0d) {
                throw new IllegalArgumentException("Tolerance value must be non-negative");
            }
            if (mode == Mode.PERCENTAGE && candidateValue > 1d) {
                throw new IllegalArgumentException("Percentage tolerance must be between 0.0 and 1.0");
            }
        }

        private void validateMinimum(double candidateMinimum) {
            if (Double.isNaN(candidateMinimum) || Double.isInfinite(candidateMinimum)) {
                throw new IllegalArgumentException("Minimum absolute tolerance must be finite");
            }
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

    private Num coordinateForIndex(int index) {
        final var series = getBarSeries();
        if (series == null || index < series.getBeginIndex() || index > series.getEndIndex()) {
            return NaN;
        }
        refreshCoordinateBase();
        if (coordinateBaseEpochMillis == Long.MIN_VALUE) {
            return NaN;
        }
        final long epochMillis = resolveEndTimeMillis(index);
        return series.numFactory().numOf(epochMillis - coordinateBaseEpochMillis);
    }

    private long resolveEndTimeMillis(int index) {
        final var series = getBarSeries();
        if (series == null || index < series.getBeginIndex() || index > series.getEndIndex()) {
            return 0L;
        }
        final Bar bar = series.getBar(index);
        return bar.getEndTime().toEpochMilli();
    }

    /**
     * Configuration for scoring weights that determine how different factors
     * contribute to trend line candidate evaluation. All weights are fractional
     * percentages between 0.0 and 1.0 and must sum to 1.0 (100%). These values are
     * serialized in indicator descriptors/JSON so user preferences round-trip.
     */
    public static final class ScoringWeights {
        /**
         * Weight for the fraction of swing points that touch the trend line. Higher
         * values favor trend lines that pass through more swing points.
         */
        public final double touchCountWeight;

        /**
         * Weight for minimizing swing points that fall outside the trend line. Higher
         * values penalize trend lines with many outside swings.
         */
        public final double outsideCountWeight;

        /**
         * Weight for whether the trend line touches the extreme swing point (lowest for
         * support, highest for resistance). Higher values favor trend lines that
         * include the extreme point.
         */
        public final double touchesExtremeWeight;

        /**
         * Weight for minimizing average deviation of swing points from the trend line.
         * Higher values favor trend lines that stay close to all swing prices.
         */
        public final double averageDeviationWeight;

        /**
         * Weight for favoring more recent anchor points. Higher values favor trend
         * lines anchored at more recent swing points.
         */
        public final double anchorRecencyWeight;

        private ScoringWeights(double touchCountWeight, double touchesExtremeWeight, double outsideCountWeight,
                double averageDeviationWeight, double anchorRecencyWeight) {
            this.touchCountWeight = touchCountWeight;
            this.outsideCountWeight = outsideCountWeight;
            this.touchesExtremeWeight = touchesExtremeWeight;
            this.averageDeviationWeight = averageDeviationWeight;
            this.anchorRecencyWeight = anchorRecencyWeight;
            validateWeights();
        }

        /**
         * Creates scoring weights from explicit fractional percentages. All weights
         * must be between 0.0 and 1.0 and must sum to 1.0.
         *
         * @param touchCountWeight       weight for swing point touch count
         * @param touchesExtremeWeight   weight for extreme point inclusion
         * @param outsideCountWeight     weight for minimizing outside swings
         * @param averageDeviationWeight weight for minimizing average deviation
         * @param anchorRecencyWeight    weight for anchor point recency
         * @return scoring weights with the specified values
         * @throws IllegalArgumentException if weights are invalid or don't sum to 1.0
         */
        public static ScoringWeights of(double touchCountWeight, double touchesExtremeWeight, double outsideCountWeight,
                double averageDeviationWeight, double anchorRecencyWeight) {
            return new ScoringWeights(touchCountWeight, touchesExtremeWeight, outsideCountWeight,
                    averageDeviationWeight, anchorRecencyWeight);
        }

        /**
         * Returns default scoring weights that provide balanced evaluation across all
         * factors. Defaults lean most heavily on price fit, keep a meaningful emphasis
         * on the extreme swing, lightly reward touch count, and preserve a small
         * recency nudge. Treat this as a “fit-first” preset without hard-coding the
         * underlying fractions elsewhere.
         *
         * @return default scoring weights
         */
        public static ScoringWeights defaultWeights() {
            return new ScoringWeights(0.10d, 0.15d, 0.05d, 0.65d, 0.05d);
        }

        /**
         * Returns preset weights with heavier emphasis on touching swing points,
         * stronger penalties for outside swings, and enough deviation control to keep
         * fits reasonable. Useful when you want trend lines that connect as many swing
         * points as possible, even if that sacrifices extreme-point anchoring.
         *
         * @return scoring weights favoring touch count
         */
        public static ScoringWeights touchCountBiasPreset() {
            return new ScoringWeights(0.55d, 0.05d, 0.20d, 0.15d, 0.05d);
        }

        /**
         * Returns preset weights with heavier emphasis on touching the extreme swing
         * point while still rewarding additional touches and keeping lines reasonably
         * close to price action. Useful when you want trend lines that definitely
         * include the most significant swing point.
         *
         * @return scoring weights favoring extreme point inclusion
         */
        public static ScoringWeights extremeSwingBiasPreset() {
            return new ScoringWeights(0.20d, 0.50d, 0.10d, 0.15d, 0.05d);
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
            private double touchCountWeight = 0.10d;
            private double outsideCountWeight = 0.05d;
            private double touchesExtremeWeight = 0.15d;
            private double averageDeviationWeight = 0.65d;
            private double anchorRecencyWeight = 0.05d;

            public Builder weightForTouchingSwingPoints(double weightFraction) {
                this.touchCountWeight = weightFraction;
                return this;
            }

            public Builder weightForTouchingExtremeSwing(double weightFraction) {
                this.touchesExtremeWeight = weightFraction;
                return this;
            }

            public Builder weightForKeepingSwingsInsideLine(double weightFraction) {
                this.outsideCountWeight = weightFraction;
                return this;
            }

            public Builder weightForStayingCloseToSwings(double weightFraction) {
                this.averageDeviationWeight = weightFraction;
                return this;
            }

            public Builder weightForRecentAnchorPoints(double weightFraction) {
                this.anchorRecencyWeight = weightFraction;
                return this;
            }

            public ScoringWeights build() {
                return new ScoringWeights(touchCountWeight, touchesExtremeWeight, outsideCountWeight,
                        averageDeviationWeight, anchorRecencyWeight);
            }
        }

        private void validateWeights() {
            validateFraction(touchCountWeight, "touchCountWeight");
            validateFraction(touchesExtremeWeight, "touchesExtremeWeight");
            validateFraction(outsideCountWeight, "outsideCountWeight");
            validateFraction(averageDeviationWeight, "averageDeviationWeight");
            validateFraction(anchorRecencyWeight, "anchorRecencyWeight");
            final double sum = touchCountWeight + touchesExtremeWeight + outsideCountWeight + averageDeviationWeight
                    + anchorRecencyWeight;
            final double epsilon = 1e-6;
            if (Math.abs(1.0d - sum) > epsilon) {
                throw new IllegalArgumentException(
                        String.format("Scoring weights must sum to 1.0 (100%%). Got %.6f", sum));
            }
        }

        private void validateFraction(double value, String label) {
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d || value > 1.0d) {
                throw new IllegalArgumentException(String
                        .format("%s must be between 0.0 and 1.0 (fractional percentage). Got %.6f", label, value));
            }
        }
    }
}
