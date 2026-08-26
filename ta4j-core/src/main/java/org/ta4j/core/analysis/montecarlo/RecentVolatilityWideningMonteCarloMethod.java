/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Composition decorator that widens an inner technique's samples when recent
 * realized volatility exceeds the state estimate.
 *
 * <p>
 * The state {@link ReturnMoments#volatility()} is an EWMA-smoothed estimate of
 * the carry volatility and therefore lags a spike in the market. When the RMS
 * of the {@linkplain MonteCarloContext#historicalLogReturns() trailing log
 * returns} in the {@code recentBarCount}-bar window exceeds the state
 * volatility, each sample is scaled outward around the inner technique's
 * empirical center (its sample mean) by the ratio:
 *
 * <pre>
 * factor = min(maxWiden, max(1, recentRealizedVol / stateVol))
 * center = mean(samples)
 * path   = center + factor * (sample - center)
 * </pre>
 *
 * <p>
 * Widening around the inner sample mean scales dispersion only and never shifts
 * the forecast location, so decorators that re-locate the inner technique (for
 * example the Normal-Inverse-Gamma and posterior-composed methods, whose center
 * is {@code h * mu}, not {@code h * drift}) stay unbiased while their quantile
 * spread grows. The factor is never below 1, so calm regimes are left
 * untouched; only the regime where the realized window is wilder than the state
 * estimate is widened. Widening is capped at {@code maxWiden} to bound the
 * effect. When the lookback window is shorter than {@code recentBarCount}, the
 * whole window is used.
 *
 * <p>
 * This decorator stresses the seam contract: it performs no random draws of its
 * own (the factor is deterministic in the window), returns exactly
 * {@code context.iterationCount()} finite samples, coerces each inner sample
 * through the context's {@link NumFactory} so cross-factory inner techniques
 * compose without throwing, propagates a {@code null} (unstable) result from
 * the inner method, and declares the forecast unstable when the inner method
 * returns the wrong sample count, the moments are not stable, the recent window
 * does not contain at least two finite returns, or the state volatility is zero
 * or non-finite.
 *
 * @see MonteCarloMethod
 * @see org.ta4j.core.indicators.forecast.state.ReturnMoments#volatility()
 * @since 0.24.2
 */
public final class RecentVolatilityWideningMonteCarloMethod implements MonteCarloMethod {

    /** Default width of the trailing realized-volatility window in bars. */
    public static final int DEFAULT_RECENT_BAR_COUNT = 10;

    /** Default upper bound on the widening factor. */
    public static final double DEFAULT_MAX_WIDEN = 4d;

    private final MonteCarloMethod inner;
    private final int recentBarCount;
    private final double maxWiden;

    /**
     * Wraps an inner technique with the default recent window
     * ({@value #DEFAULT_RECENT_BAR_COUNT} bars) and widening bound
     * ({@value #DEFAULT_MAX_WIDEN}).
     *
     * @param inner technique whose samples are widened
     * @since 0.24.2
     */
    public RecentVolatilityWideningMonteCarloMethod(MonteCarloMethod inner) {
        this(inner, DEFAULT_RECENT_BAR_COUNT, DEFAULT_MAX_WIDEN);
    }

    /**
     * Wraps an inner technique with a configurable recent window and widening
     * bound.
     *
     * @param inner          technique whose samples are widened
     * @param recentBarCount trailing window length in bars for the realized
     *                       volatility, must be at least 2
     * @param maxWiden       upper bound on the widening factor, must be &gt;= 1
     * @since 0.24.2
     */
    public RecentVolatilityWideningMonteCarloMethod(MonteCarloMethod inner, int recentBarCount, double maxWiden) {
        if (inner == null) {
            throw new IllegalArgumentException("inner must not be null");
        }
        if (recentBarCount < 2) {
            throw new IllegalArgumentException("recentBarCount must be >= 2");
        }
        if (maxWiden < 1d || !Double.isFinite(maxWiden)) {
            throw new IllegalArgumentException("maxWiden must be a finite value >= 1");
        }
        this.inner = inner;
        this.recentBarCount = recentBarCount;
        this.maxWiden = maxWiden;
    }

    @Override
    public List<Num> terminalReturns(MonteCarloContext context) {
        List<Num> samples = inner.terminalReturns(context);
        if (samples == null || samples.size() != context.iterationCount()) {
            return null;
        }
        ReturnMoments moments = context.moments();
        if (moments == null || !moments.isStable()) {
            return null;
        }
        NumFactory numFactory = context.numFactory();
        Num stateVolatility = moments.volatility();
        if (!Num.isFinite(stateVolatility) || stateVolatility.isNegative() || stateVolatility.isZero()) {
            return null;
        }
        Num recentRealized = recentVolatilityRms(context.historicalLogReturns(), numFactory);
        if (recentRealized == null || !Num.isFinite(recentRealized)) {
            return null;
        }
        double ratio = recentRealized.doubleValue() / stateVolatility.doubleValue();
        double factor = Math.min(maxWiden, Math.max(1d, ratio));

        // Coerce to the context factory and locate the inner distribution's empirical
        // center so widening scales dispersion without shifting the forecast location.
        List<Num> converted = new ArrayList<>(context.iterationCount());
        Num center = numFactory.zero();
        for (Num sample : samples) {
            Num normalized = normalize(sample, numFactory);
            if (normalized == null) {
                return null;
            }
            converted.add(normalized);
            center = center.plus(normalized);
        }
        center = center.dividedBy(numFactory.numOf(converted.size()));
        if (!Num.isFinite(center)) {
            return null;
        }

        List<Num> widened = new ArrayList<>(context.iterationCount());
        for (Num sample : converted) {
            Num centered = sample.minus(center);
            Num scaled = center.plus(centered.multipliedBy(numFactory.numOf(factor)));
            if (!Num.isFinite(scaled)) {
                return null;
            }
            widened.add(scaled);
        }
        return widened;
    }

    private static Num normalize(Num value, NumFactory numFactory) {
        return Num.isFinite(value) ? numFactory.numOf(value.bigDecimalValue()) : null;
    }

    /**
     * RMS of the trailing realized returns, accumulated in the active {@code Num}
     * domain so magnitudes beyond the primitive {@code double} range neither
     * overflow (squares to infinity) nor underflow (tiny squares to zero) before
     * the root. Returns {@code null} when the window does not contain at least two
     * finite returns.
     */
    private Num recentVolatilityRms(List<Num> window, NumFactory numFactory) {
        int from = Math.max(0, window.size() - recentBarCount);
        int count = 0;
        Num sumSquares = numFactory.zero();
        for (int i = from; i < window.size(); i++) {
            Num value = window.get(i);
            if (!Num.isFinite(value)) {
                return null;
            }
            sumSquares = sumSquares.plus(value.multipliedBy(value));
            count++;
        }
        if (count < 2) {
            return null;
        }
        return sumSquares.dividedBy(numFactory.numOf(count)).sqrt();
    }

    @Override
    public String toString() {
        return "RecentVolatilityWideningMonteCarloMethod[recentBarCount=" + recentBarCount + ", maxWiden=" + maxWiden
                + ", inner=" + inner + "]";
    }
}