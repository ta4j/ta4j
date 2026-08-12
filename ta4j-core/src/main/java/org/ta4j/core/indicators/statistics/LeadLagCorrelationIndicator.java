/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Rolling lead/lag correlation indicator: scans an inclusive lag range at the
 * evaluated index and reports the selected correlation, with a full
 * {@link Profile} diagnostics view.
 *
 * <p>
 * Each lag is evaluated with the exact window semantics of
 * {@link LaggedCorrelationIndicator}: {@code barCount} aligned samples ending
 * at the evaluated index, with the first indicator shifted by the lag. A lag
 * that lacks sufficient history, or whose window has no variance, is retained
 * in the profile as undefined instead of silently shrinking its sample count,
 * so all defined lags are compared over equally sized windows.
 * </p>
 *
 * <p>
 * The sign convention matches {@link LaggedCorrelationIndicator}: positive lag
 * means the first indicator leads the second, negative lag means it trails.
 * Calculations never read beyond the evaluated index. {@code getValue(index)}
 * remains {@code NaN} until the full configured lag range reaches its worst-lag
 * warm-up boundary (even if inner lags are already defined), and is also
 * {@code NaN} when no lag in the range is defined; otherwise it is the
 * correlation at the selected lag. {@code getProfile(index)} re-scans the full
 * profile at that index.
 * </p>
 *
 * <p>
 * Complexity for {@code L = maximumLag - minimumLag + 1} lags is
 * {@code O(L * barCount)} time with {@code O(barCount)} scratch memory per lag;
 * no per-lag cached indicators are created, and the profile is recomputed on
 * each access instead of being cached.
 * </p>
 *
 * @since 0.24.2
 */
public final class LeadLagCorrelationIndicator extends CachedIndicator<Num> {

    /**
     * Maximum number of lags a single profile may scan. Guards the profile
     * allocation and the {@code O(L * barCount)} scan against integer overflow and
     * memory exhaustion from hostile lag ranges.
     */
    static final long MAX_PROFILE_LAGS = 1_000_000L;

    private final Indicator<Num> first;
    private final Indicator<Num> second;
    private final int barCount;
    private final int minimumLag;
    private final int maximumLag;
    private final LagSelectionPolicy selectionPolicy;

    /**
     * Constructor.
     *
     * @param first           first numeric indicator
     * @param second          second numeric indicator
     * @param barCount        aligned window length shared by every lag, at least 2
     * @param minimumLag      inclusive lower lag bound
     * @param maximumLag      inclusive upper lag bound, at least {@code minimumLag}
     * @param selectionPolicy policy for choosing {@code bestLags}
     * @throws IllegalArgumentException if {@code barCount < 2},
     *                                  {@code minimumLag > maximumLag}, a lag bound
     *                                  is too large to index safely, the lag range
     *                                  exceeds the profile capacity guard, or the
     *                                  indicators use different series
     * @throws NullPointerException     if an indicator or the policy is null
     * @since 0.24.2
     */
    public LeadLagCorrelationIndicator(Indicator<Num> first, Indicator<Num> second, int barCount, int minimumLag,
            int maximumLag, LagSelectionPolicy selectionPolicy) {
        super(first);
        IndicatorUtils.requireSameSeries(first, second);
        this.first = first;
        this.second = second;
        this.barCount = CorrelationWindowSupport.validateBarCount(barCount);
        if (minimumLag > maximumLag) {
            throw new IllegalArgumentException("minimumLag must be <= maximumLag");
        }
        CorrelationWindowSupport.validateLag(minimumLag, this.barCount);
        CorrelationWindowSupport.validateLag(maximumLag, this.barCount);
        if ((long) maximumLag - minimumLag + 1L > MAX_PROFILE_LAGS) {
            throw new IllegalArgumentException("lag range is too large (at most " + MAX_PROFILE_LAGS + " lags)");
        }
        this.minimumLag = minimumLag;
        this.maximumLag = maximumLag;
        this.selectionPolicy = Objects.requireNonNull(selectionPolicy, "selectionPolicy");
    }

    /**
     * Convenience constructor with a symmetric lag range
     * {@code [-maximumLag, maximumLag]}.
     *
     * @param first           first numeric indicator
     * @param second          second numeric indicator
     * @param barCount        aligned window length shared by every lag, at least 2
     * @param maximumLag      inclusive absolute bound of the symmetric lag range
     * @param selectionPolicy policy for choosing {@code bestLags}
     * @throws IllegalArgumentException if {@code barCount < 2}, a lag bound is too
     *                                  large to index safely, the lag range exceeds
     *                                  the profile capacity guard, or the
     *                                  indicators use different series
     * @throws NullPointerException     if an indicator or the policy is null
     * @since 0.24.2
     */
    public LeadLagCorrelationIndicator(Indicator<Num> first, Indicator<Num> second, int barCount, int maximumLag,
            LagSelectionPolicy selectionPolicy) {
        this(first, second, barCount, -maximumLag, maximumLag, selectionPolicy);
    }

    /**
     * @param index the evaluation index
     * @return the correlation at the selected lag, or {@code NaN} when no lag in
     *         the range is defined at that index
     */
    @Override
    protected Num calculate(int index) {
        // The unstable-bar boundary is relative to the retained series head, so
        // absolute indexes need the begin-index offset; long arithmetic keeps
        // the comparison overflow-proof at the extremes of the int range and
        // uses the exact (un-clamped) boundary so a saturated published count
        // can never make an unreachable warm-up boundary look complete.
        if ((long) index < (long) getBarSeries().getBeginIndex() + exactUnstableBars()) {
            return NaN.NaN;
        }
        return scanProfile(index).selectedCorrelation();
    }

    /**
     * The boundary below which no lag in the range can have a complete window: the
     * worst lagged unstable-bar boundary over the whole range. The profile itself
     * retains per-lag undefined points below this boundary.
     *
     * <p>
     * When the exact boundary exceeds {@code Integer.MAX_VALUE}, the published
     * count saturates at {@code Integer.MAX_VALUE}; availability guards use the
     * exact long boundary internally so the saturation never makes an unreachable
     * warm-up boundary look reachable.
     * </p>
     *
     * @return the number of unstable bars
     */
    @Override
    public int getCountOfUnstableBars() {
        return CorrelationWindowSupport.clampUnstableBars(exactUnstableBars());
    }

    /**
     * The exact (un-clamped) worst-lag unstable-bar boundary over the whole
     * configured lag range, relative to the retained series head.
     */
    private long exactUnstableBars() {
        // The worst lag is maximumLag for the first-indicator offset and
        // minimumLag for the second-indicator offset; long arithmetic keeps the
        // extremes of the int range overflow-proof.
        long firstUnstable = (long) first.getCountOfUnstableBars() + Math.max((long) maximumLag, 0L);
        long secondUnstable = (long) second.getCountOfUnstableBars() + Math.max(-(long) minimumLag, 0L);
        return Math.max(firstUnstable, secondUnstable) + (long) barCount - 1L;
    }

    /**
     * Re-scans the full correlation profile at the given index.
     *
     * <p>
     * The profile is not cached: each call re-runs the {@code O(L * barCount)}
     * scan, so callers should avoid repeated profile access on hot paths.
     * {@link #getValue(int)} shares the same scan and is the cached accessor.
     * </p>
     *
     * @param index the evaluation index, inside the series
     * @return the immutable profile, one point per lag in ascending lag order
     * @throws IllegalArgumentException if {@code index} is outside the series
     * @since 0.24.2
     */
    public Profile getProfile(int index) {
        if (index < getBarSeries().getBeginIndex() || index > getBarSeries().getEndIndex()) {
            throw new IllegalArgumentException("index must be within the series");
        }
        return scanProfile(index);
    }

    private Profile scanProfile(int index) {
        NumFactory numFactory = getBarSeries().numFactory();
        List<Point> points = new ArrayList<>(maximumLag - minimumLag + 1);
        Num bestScore = null;
        // The long counter keeps the scan overflow-proof even at the extreme
        // ends of the int range; per-lag validation still bounds every lag.
        for (long lag = minimumLag; lag <= maximumLag; lag++) {
            int lagIndex = (int) lag;
            Point point = lagPoint(first, second, index, barCount, lagIndex, numFactory,
                    getBarSeries().getBeginIndex());
            points.add(point);
            if (point.isDefined()) {
                Num score = selectionScore(point.correlation(), selectionPolicy);
                if (bestScore == null || score.compareTo(bestScore) > 0) {
                    bestScore = score;
                }
            }
        }

        List<Integer> bestLags = new ArrayList<>();
        for (Point point : points) {
            if (!point.isDefined()) {
                continue;
            }
            Num score = selectionScore(point.correlation(), selectionPolicy);
            if (score.compareTo(bestScore) == 0) {
                bestLags.add(point.lag());
            }
        }

        if (bestLags.isEmpty()) {
            return new Profile(index, barCount, minimumLag, maximumLag, selectionPolicy, points, bestLags,
                    OptionalInt.empty(), NaN.NaN);
        }
        int selectedLag = bestLags.get(0);
        for (int i = 1; i < bestLags.size(); i++) {
            int candidate = bestLags.get(i);
            // Long arithmetic keeps the tie-break overflow-safe at the extremes
            // of the int range.
            long candidateDistance = Math.abs((long) candidate);
            long selectedDistance = Math.abs((long) selectedLag);
            if (candidateDistance < selectedDistance
                    || (candidateDistance == selectedDistance && candidate < selectedLag)) {
                selectedLag = candidate;
            }
        }
        Num selectedCorrelation = null;
        for (Point point : points) {
            if (point.lag() == selectedLag) {
                selectedCorrelation = point.correlation();
                break;
            }
        }
        return new Profile(index, barCount, minimumLag, maximumLag, selectionPolicy, points, bestLags,
                OptionalInt.of(selectedLag), selectedCorrelation);
    }

    private static Point lagPoint(Indicator<Num> first, Indicator<Num> second, int endIndex, int barCount, int lag,
            NumFactory numFactory, int seriesBeginIndex) {
        // Mirror LaggedCorrelationIndicator's unstable-bar boundary exactly:
        // indexes below the lagged unstable-bar count must stay undefined even
        // when the underlying indicators emit finite values during their
        // warm-up. The count is relative to the retained series head, so the
        // absolute end index needs the begin-index offset; long arithmetic
        // keeps the comparison overflow-proof at the extremes of the int range
        // and uses the exact (un-clamped) boundary so a saturated published
        // count can never make an unreachable warm-up boundary look complete.
        if ((long) endIndex < (long) seriesBeginIndex
                + CorrelationWindowSupport.laggedUnstableBarsAsLong(barCount, lag, first, second)) {
            return new Point(lag, NaN.NaN, 0);
        }
        CorrelationWindowSupport.NumericWindow window = CorrelationWindowSupport.laggedWindow(first, second, endIndex,
                barCount, lag);
        if (window == null) {
            return new Point(lag, NaN.NaN, 0);
        }
        return new Point(lag, CorrelationWindowSupport.pearson(numFactory, window), barCount);
    }

    private static Num selectionScore(Num correlation, LagSelectionPolicy selectionPolicy) {
        return selectionPolicy == LagSelectionPolicy.MAXIMUM_CORRELATION ? correlation : correlation.abs();
    }

    /**
     * Immutable result of one {@link LeadLagCorrelationIndicator#getProfile(int)}
     * scan: the full correlation profile over the configured lag range plus the
     * deterministic best-lag selection.
     *
     * <p>
     * {@link #points()} always contains one entry per lag in ascending lag order,
     * including undefined lags. {@link #bestLags()} contains every lag whose
     * correlation ties for the best score under the selection policy, in ascending
     * order; it is empty when no lag is defined. {@link #selectedLag()} is one
     * deterministic pick from {@link #bestLags()}: the smallest absolute lag, then
     * the smallest signed lag, so a symmetric {@code [-k, k]} tie resolves to
     * {@code -k}. The selected correlation keeps its original sign even when the
     * absolute-correlation policy did the selection.
     * </p>
     *
     * @param endIndex            the evaluation index the profile is anchored at
     * @param barCount            the aligned window length shared by all lags
     * @param minimumLag          inclusive lower lag bound of the scan
     * @param maximumLag          inclusive upper lag bound of the scan
     * @param selectionPolicy     the policy used to pick {@link #bestLags()}
     * @param points              one point per lag, ascending, undefined lags
     *                            retained
     * @param bestLags            all lags tying for the best score, ascending;
     *                            empty when no lag is defined
     * @param selectedLag         deterministic pick from {@code bestLags}; empty
     *                            when no lag is defined
     * @param selectedCorrelation the signed correlation at {@code selectedLag}, or
     *                            {@code NaN} when no lag is defined
     * @since 0.24.2
     */
    public record Profile(int endIndex, int barCount, int minimumLag, int maximumLag,
            LagSelectionPolicy selectionPolicy, List<Point> points, List<Integer> bestLags, OptionalInt selectedLag,
            Num selectedCorrelation) {

        /**
         * Validates the profile and makes the lists unmodifiable.
         *
         * @throws NullPointerException     if {@code selectionPolicy}, {@code points},
         *                                  {@code bestLags}, {@code selectedLag}, or
         *                                  {@code selectedCorrelation} is null
         * @throws IllegalArgumentException if {@code barCount < 2},
         *                                  {@code minimumLag > maximumLag},
         *                                  {@code bestLags} is not ascending, a point's
         *                                  sampleCount is neither 0 nor
         *                                  {@code barCount}, or the selected
         *                                  lag/correlation combination is inconsistent
         */
        public Profile {
            if (barCount < 2) {
                throw new IllegalArgumentException("barCount must be >= 2");
            }
            if (minimumLag > maximumLag) {
                throw new IllegalArgumentException("minimumLag must be <= maximumLag");
            }
            Objects.requireNonNull(selectionPolicy, "selectionPolicy");
            points = List.copyOf(points);
            bestLags = List.copyOf(bestLags);
            Objects.requireNonNull(selectedLag, "selectedLag");
            Objects.requireNonNull(selectedCorrelation, "selectedCorrelation");
            long expectedPointCount = (long) maximumLag - minimumLag + 1L;
            if (points.size() != expectedPointCount) {
                throw new IllegalArgumentException("points must contain one point per requested lag");
            }
            for (int index = 0; index < points.size(); index++) {
                long expectedLag = (long) minimumLag + index;
                if (points.get(index).lag() != expectedLag) {
                    throw new IllegalArgumentException("points must be in ascending requested-lag order");
                }
            }
            for (Point point : points) {
                // A window either supplies the full aligned barCount samples or
                // is unavailable (0 samples); a partial count would compare
                // unequal window lengths across lags and cannot be produced by
                // this indicator.
                if (point.sampleCount() != 0 && point.sampleCount() != barCount) {
                    throw new IllegalArgumentException("each point's sampleCount must be 0 or the profile's barCount");
                }
            }
            Integer previousLag = null;
            for (Integer lag : bestLags) {
                if (previousLag != null && lag <= previousLag) {
                    throw new IllegalArgumentException("bestLags must be strictly ascending");
                }
                previousLag = lag;
            }
            boolean selectedDefined = selectedLag.isPresent();
            boolean correlationDefined = !selectedCorrelation.isNaN();
            if (selectedDefined != correlationDefined) {
                throw new IllegalArgumentException(
                        "selectedLag and selectedCorrelation must be defined together or both undefined");
            }
            // Derive the expected selection from the points themselves so a caller
            // cannot supply a self-consistent but wrong best-lag list or tie-break.
            Num bestScore = null;
            for (Point point : points) {
                if (!point.isDefined()) {
                    continue;
                }
                Num score = selectionScore(point.correlation(), selectionPolicy);
                if (bestScore == null || score.compareTo(bestScore) > 0) {
                    bestScore = score;
                }
            }
            List<Integer> expectedBestLags = new ArrayList<>();
            if (bestScore != null) {
                for (Point point : points) {
                    if (!point.isDefined()) {
                        continue;
                    }
                    if (selectionScore(point.correlation(), selectionPolicy).compareTo(bestScore) == 0) {
                        expectedBestLags.add(point.lag());
                    }
                }
            }
            if (!bestLags.equals(expectedBestLags)) {
                throw new IllegalArgumentException("bestLags must contain exactly the maximal-scoring lags, ascending");
            }
            OptionalInt expectedSelectedLag = OptionalInt.empty();
            if (!expectedBestLags.isEmpty()) {
                int selected = expectedBestLags.get(0);
                for (int i = 1; i < expectedBestLags.size(); i++) {
                    int candidate = expectedBestLags.get(i);
                    // Long arithmetic keeps the tie-break overflow-safe at the
                    // extremes of the int range.
                    long candidateDistance = Math.abs((long) candidate);
                    long selectedDistance = Math.abs((long) selected);
                    if (candidateDistance < selectedDistance
                            || (candidateDistance == selectedDistance && candidate < selected)) {
                        selected = candidate;
                    }
                }
                expectedSelectedLag = OptionalInt.of(selected);
            }
            if (!selectedLag.equals(expectedSelectedLag)) {
                throw new IllegalArgumentException("selectedLag must be the deterministic pick from bestLags");
            }
            if (selectedDefined) {
                // A single lookup: scan for the lag instead of materializing a
                // map of every point, which for a wide lag range would allocate
                // a new entry per evaluated lag.
                int selectedLagValue = selectedLag.getAsInt();
                Point selectedPoint = null;
                for (Point point : points) {
                    if (point.lag() == selectedLagValue) {
                        selectedPoint = point;
                        break;
                    }
                }
                if (selectedPoint == null || selectedPoint.correlation().compareTo(selectedCorrelation) != 0) {
                    throw new IllegalArgumentException(
                            "selectedCorrelation must equal the correlation of the selected point");
                }
            }
        }
    }

    /**
     * One lag's correlation in a {@link Profile}.
     *
     * @param lag         the lag, following the {@link LaggedCorrelationIndicator}
     *                    sign convention (positive means the first indicator leads
     *                    the second)
     * @param correlation the Pearson correlation at this lag, or {@code NaN} when
     *                    the lag is undefined
     * @param sampleCount aligned samples actually available in the window, or
     *                    {@code 0} when the window itself was unavailable
     * @since 0.24.2
     */
    public record Point(int lag, Num correlation, int sampleCount) {

        /**
         * How far a finite correlation may deviate from {@code [-1, 1]}: computed
         * Pearson values can exceed the mathematical bound by rounding noise only.
         */
        private static final double MAX_CORRELATION_ROUNDING = 1.0e-12;

        /**
         * Validates the point.
         *
         * @throws NullPointerException     if {@code correlation} is null
         * @throws IllegalArgumentException if {@code sampleCount} is negative, the
         *                                  correlation is finite with fewer than two
         *                                  samples, or the correlation lies outside
         *                                  {@code [-1, 1]} beyond rounding tolerance
         */
        public Point {
            Objects.requireNonNull(correlation, "correlation");
            if (sampleCount < 0) {
                throw new IllegalArgumentException("sampleCount must be >= 0");
            }
            if (!correlation.isNaN()) {
                if (!CorrelationWindowSupport.isFinite(correlation)) {
                    throw new IllegalArgumentException("correlation must be finite or NaN");
                }
                double value = correlation.doubleValue();
                if (value < -1.0 - MAX_CORRELATION_ROUNDING || value > 1.0 + MAX_CORRELATION_ROUNDING) {
                    throw new IllegalArgumentException("a finite correlation must lie in [-1, 1]");
                }
                if (sampleCount < 2) {
                    throw new IllegalArgumentException("a finite correlation requires at least 2 samples");
                }
            }
        }

        /**
         * @return {@code true} when the correlation is a defined (finite) number
         * @since 0.24.2
         */
        public boolean isDefined() {
            return !correlation.isNaN();
        }
    }

    /**
     * Policy for choosing the best lags of a {@link Profile}.
     *
     * @since 0.24.2
     */
    public enum LagSelectionPolicy {

        /**
         * Select the lag with the highest signed correlation.
         */
        MAXIMUM_CORRELATION,
        /**
         * Select the lag with the highest absolute correlation; the reported
         * correlation keeps its original sign.
         */
        MAXIMUM_ABSOLUTE_CORRELATION
    }
}
