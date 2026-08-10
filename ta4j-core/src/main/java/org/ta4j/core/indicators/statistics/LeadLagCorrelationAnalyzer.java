/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Scans an inclusive lag range and returns the full lead/lag correlation
 * profile plus a deterministic optimal lag.
 *
 * <p>
 * Each lag is evaluated with the exact window semantics of
 * {@link LaggedCorrelationIndicator}: {@code barCount} aligned samples ending
 * at {@code endIndex}, with the first indicator shifted by the lag. A lag that
 * lacks sufficient history, or whose window has no variance, is retained in the
 * profile as undefined instead of silently shrinking its sample count, so all
 * defined lags are compared over equally sized windows.
 * </p>
 *
 * <p>
 * The sign convention matches {@link LaggedCorrelationIndicator}: positive lag
 * means the first indicator leads the second, negative lag means it trails.
 * Calculations never read beyond {@code endIndex}.
 * </p>
 *
 * <p>
 * Complexity for {@code L = maximumLag - minimumLag + 1} lags is
 * {@code O(L * barCount)} time with {@code O(barCount)} scratch memory per lag;
 * no per-lag cached indicators are created.
 * </p>
 *
 * @since 0.24.1
 */
public final class LeadLagCorrelationAnalyzer {

    /**
     * Maximum number of lags a single profile may scan. Guards the profile
     * allocation and the {@code O(L * barCount)} scan against integer overflow and
     * memory exhaustion from hostile lag ranges.
     */
    static final long MAX_PROFILE_LAGS = 1_000_000L;

    /**
     * Creates an analyzer.
     */
    public LeadLagCorrelationAnalyzer() {
    }

    /**
     * Analyzes the correlation profile of {@code first} against {@code second} over
     * the inclusive lag range {@code [minimumLag, maximumLag]}.
     *
     * @param first           first numeric indicator
     * @param second          second numeric indicator
     * @param endIndex        evaluation index; no sample is read beyond it
     * @param barCount        aligned window length shared by every lag, at least 2
     * @param minimumLag      inclusive lower lag bound
     * @param maximumLag      inclusive upper lag bound, at least {@code minimumLag}
     * @param selectionPolicy policy for choosing {@code bestLags}
     * @return the immutable profile, one point per lag in ascending lag order
     * @throws IllegalArgumentException if {@code barCount < 2},
     *                                  {@code minimumLag > maximumLag}, a lag bound
     *                                  is too large to index safely,
     *                                  {@code endIndex} is outside the series, or
     *                                  the indicators use different series
     * @throws NullPointerException     if an indicator or the policy is null
     */
    public LagCorrelationProfile analyze(Indicator<Num> first, Indicator<Num> second, int endIndex, int barCount,
            int minimumLag, int maximumLag, LagSelectionPolicy selectionPolicy) {
        IndicatorUtils.requireSameSeries(first, second);
        Objects.requireNonNull(selectionPolicy, "selectionPolicy");
        BarSeries series = first.getBarSeries();
        if (endIndex < series.getBeginIndex() || endIndex > series.getEndIndex()) {
            throw new IllegalArgumentException("endIndex must be within the series");
        }
        int validatedBarCount = CorrelationWindowSupport.validateBarCount(barCount);
        if (minimumLag > maximumLag) {
            throw new IllegalArgumentException("minimumLag must be <= maximumLag");
        }
        CorrelationWindowSupport.validateLag(minimumLag, validatedBarCount);
        CorrelationWindowSupport.validateLag(maximumLag, validatedBarCount);
        if ((long) maximumLag - minimumLag + 1L > MAX_PROFILE_LAGS) {
            throw new IllegalArgumentException("lag range is too large (at most " + MAX_PROFILE_LAGS + " lags)");
        }

        NumFactory numFactory = series.numFactory();
        List<LagCorrelationPoint> points = new ArrayList<>(maximumLag - minimumLag + 1);
        Num bestScore = null;
        // The long counter keeps the scan overflow-proof even at the extreme
        // ends of the int range; per-lag validation still bounds every lag.
        for (long lag = minimumLag; lag <= maximumLag; lag++) {
            int lagIndex = (int) lag;
            LagCorrelationPoint point = lagPoint(first, second, endIndex, validatedBarCount, lagIndex, numFactory);
            points.add(point);
            if (point.isDefined()) {
                Num score = selectionPolicy == LagSelectionPolicy.MAXIMUM_CORRELATION ? point.correlation()
                        : point.correlation().abs();
                if (bestScore == null || score.compareTo(bestScore) > 0) {
                    bestScore = score;
                }
            }
        }

        List<Integer> bestLags = new ArrayList<>();
        for (LagCorrelationPoint point : points) {
            if (!point.isDefined()) {
                continue;
            }
            Num score = selectionPolicy == LagSelectionPolicy.MAXIMUM_CORRELATION ? point.correlation()
                    : point.correlation().abs();
            if (score.compareTo(bestScore) == 0) {
                bestLags.add(point.lag());
            }
        }

        if (bestLags.isEmpty()) {
            return new LagCorrelationProfile(endIndex, validatedBarCount, minimumLag, maximumLag, selectionPolicy,
                    points, bestLags, OptionalInt.empty(), NaN.NaN);
        }
        int selectedLag = bestLags.get(0);
        for (int i = 1; i < bestLags.size(); i++) {
            int candidate = bestLags.get(i);
            int candidateDistance = Math.abs(candidate);
            int selectedDistance = Math.abs(selectedLag);
            if (candidateDistance < selectedDistance
                    || (candidateDistance == selectedDistance && candidate < selectedLag)) {
                selectedLag = candidate;
            }
        }
        Num selectedCorrelation = null;
        for (LagCorrelationPoint point : points) {
            if (point.lag() == selectedLag) {
                selectedCorrelation = point.correlation();
                break;
            }
        }
        return new LagCorrelationProfile(endIndex, validatedBarCount, minimumLag, maximumLag, selectionPolicy, points,
                bestLags, OptionalInt.of(selectedLag), selectedCorrelation);
    }

    private static LagCorrelationPoint lagPoint(Indicator<Num> first, Indicator<Num> second, int endIndex, int barCount,
            int lag, NumFactory numFactory) {
        // Mirror LaggedCorrelationIndicator exactly: indexes below the lagged
        // unstable-bar boundary must stay undefined even when the underlying
        // indicators emit finite values during their warm-up.
        if (endIndex < CorrelationWindowSupport.laggedUnstableBars(barCount, lag, first, second)) {
            return new LagCorrelationPoint(lag, NaN.NaN, 0);
        }
        CorrelationWindowSupport.NumericWindow window = CorrelationWindowSupport.laggedWindow(first, second, endIndex,
                barCount, lag);
        if (window == null) {
            return new LagCorrelationPoint(lag, NaN.NaN, 0);
        }
        return new LagCorrelationPoint(lag, CorrelationWindowSupport.pearson(numFactory, window), barCount);
    }
}
