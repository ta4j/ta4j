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
 * Composition decorator that widens an inner technique's centered samples when
 * recent realized volatility exceeds the state estimate.
 *
 * <p>
 * The state {@link ReturnMoments#volatility()} is an EWMA-smoothed estimate of
 * the carry volatility and therefore lags a spike in the market. When the RMS
 * of the {@linkplain MonteCarloContext#historicalLogReturns() trailing log
 * returns} in the {@code recentBarCount}-bar window exceeds the state
 * volatility, each centered sample is scaled outward by the ratio:
 *
 * <pre>
 * factor = min(maxWiden, max(1, recentRealizedVol / stateVol))
 * path   = h * drift + factor * (sample - h * drift)
 * </pre>
 *
 * <p>
 * The factor is never below 1, so calm regimes are left untouched; only the
 * regime where the realized window is wilder than the state estimate is
 * widened. Widening is capped at {@code maxWiden} to bound the effect. When the
 * lookback window is shorter than {@code recentBarCount}, the whole window is
 * used.
 *
 * <p>
 * This decorator stresses the seam contract: it performs no random draws of its
 * own (the factor is deterministic in the window), returns exactly
 * {@code context.iterationCount()} finite samples, propagates a {@code null}
 * (unstable) result from the inner method, and declares the forecast unstable
 * when the inner method returns the wrong sample count, the moments are not
 * stable, the recent window does not contain at least two finite returns, or
 * the state volatility is zero or non-finite.
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
     * @param inner technique whose centered samples are widened
     * @since 0.24.2
     */
    public RecentVolatilityWideningMonteCarloMethod(MonteCarloMethod inner) {
        this(inner, DEFAULT_RECENT_BAR_COUNT, DEFAULT_MAX_WIDEN);
    }

    /**
     * Wraps an inner technique with a configurable recent window and widening
     * bound.
     *
     * @param inner          technique whose centered samples are widened
     * @param recentBarCount trailing window length in bars for the realized
     *                       volatility, must be at least 2
     * @param maxWiden       upper bound on the widening factor, must be >= 1
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
        Num drift = moments.drift();
        Num stateVolatility = moments.volatility();
        if (!Num.isFinite(drift) || !Num.isFinite(stateVolatility) || stateVolatility.isNegative()
                || stateVolatility.isZero()) {
            return null;
        }
        double recentRealized = recentVolatilityRms(context.historicalLogReturns());
        if (!Double.isFinite(recentRealized)) {
            return null;
        }
        double ratio = recentRealized / stateVolatility.doubleValue();
        double factor = Math.min(maxWiden, Math.max(1d, ratio));
        Num driftPath = drift.multipliedBy(numFactory.numOf(context.horizon()));
        List<Num> widened = new ArrayList<>(context.iterationCount());
        for (Num sample : samples) {
            Num centered = sample.minus(driftPath);
            Num scaled = driftPath.plus(centered.multipliedBy(numFactory.numOf(factor)));
            if (!Num.isFinite(scaled)) {
                return null;
            }
            widened.add(scaled);
        }
        return widened;
    }

    /**
     * RMS of the recent trailing log returns in the window, or {@link Double#NaN}
     * when the trailing {@code recentBarCount} bars do not contain at least two
     * finite returns.
     */
    private double recentVolatilityRms(List<Num> window) {
        int start = Math.max(0, window.size() - recentBarCount);
        if (window.size() - start < 2) {
            return Double.NaN;
        }
        double sum = 0d;
        int count = 0;
        for (int i = start; i < window.size(); i++) {
            Num value = window.get(i);
            if (!Num.isFinite(value)) {
                return Double.NaN;
            }
            double doubleValue = value.doubleValue();
            sum += doubleValue * doubleValue;
            count++;
        }
        return Math.sqrt(sum / count);
    }

    @Override
    public String toString() {
        return "RecentVolatilityWideningMonteCarloMethod[recentBarCount=" + recentBarCount + ", maxWiden=" + maxWiden
                + ", inner=" + inner + "]";
    }
}