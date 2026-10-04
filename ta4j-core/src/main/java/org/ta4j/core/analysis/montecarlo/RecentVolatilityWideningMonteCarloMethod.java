/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Recent-volatility widening behind
 * {@link MonteCarloMethod#widenedByRecentVolatility(int, double)}.
 *
 * <p>
 * The state {@link ReturnMoments#volatility()} is a smoothed estimate that lags
 * a volatility spike. When the RMS of the trailing {@code recentBarCount}
 * historical log returns exceeds it, the inner samples are scaled outward
 * around their own mean:
 *
 * <pre>
 * factor = min(maxWiden, max(1, recentRealizedVol / stateVol))
 * path   = mean(samples) + factor * (sample - mean(samples))
 * </pre>
 *
 * Widening around the sample mean never shifts location, so re-locating inner
 * techniques (whose center is not {@code h * drift}) stay unbiased. The
 * forecast is unstable when the inner technique breaks the seam contract, the
 * moments are unstable, the recent window holds fewer than two finite returns,
 * or the state volatility is zero or non-finite.
 */
final class RecentVolatilityWideningMonteCarloMethod implements MonteCarloMethod {

    /** Default width of the trailing realized-volatility window in bars. */
    static final int DEFAULT_RECENT_BAR_COUNT = 10;

    /** Default upper bound on the widening factor. */
    static final double DEFAULT_MAX_WIDEN = 4d;

    private final MonteCarloMethod inner;
    private final int recentBarCount;
    private final double maxWiden;

    RecentVolatilityWideningMonteCarloMethod(MonteCarloMethod inner) {
        this(inner, DEFAULT_RECENT_BAR_COUNT, DEFAULT_MAX_WIDEN);
    }

    RecentVolatilityWideningMonteCarloMethod(MonteCarloMethod inner, int recentBarCount, double maxWiden) {
        if (recentBarCount < 2) {
            throw new IllegalArgumentException("recentBarCount must be >= 2");
        }
        if (maxWiden < 1d || !Double.isFinite(maxWiden)) {
            throw new IllegalArgumentException("maxWiden must be a finite value >= 1");
        }
        this.inner = Objects.requireNonNull(inner, "inner");
        this.recentBarCount = recentBarCount;
        this.maxWiden = maxWiden;
    }

    @Override
    public List<Num> terminalReturns(MonteCarloContext context) {
        List<Num> samples = MonteCarloArithmetic.normalizeSamples(inner.terminalReturns(context), context);
        if (samples == null) {
            return null;
        }
        ReturnMoments moments = context.moments();
        if (moments == null || !moments.isStable()) {
            return null;
        }
        NumFactory numFactory = context.numFactory();
        Num stateVolatility = MonteCarloArithmetic.normalize(moments.volatility(), numFactory);
        if (stateVolatility == null || stateVolatility.isNegative() || stateVolatility.isZero()) {
            return null;
        }
        Num recentRealized = recentVolatilityRms(context.historicalLogReturns(), numFactory);
        if (recentRealized == null || !Num.isFinite(recentRealized)) {
            return null;
        }
        Num ratio = recentRealized.dividedBy(stateVolatility);
        Num one = numFactory.one();
        Num cap = numFactory.numOf(maxWiden);
        Num factor = ratio.compareTo(one) < 0 ? one : ratio.compareTo(cap) > 0 ? cap : ratio;
        // A factor of one leaves every sample unchanged, so no empirical center is
        // necessary (or safe to accumulate).
        if (factor.compareTo(one) == 0) {
            return samples;
        }
        Num center = numFactory.zero();
        int count = 0;
        for (Num sample : samples) {
            Num divisor = numFactory.numOf(++count);
            // Same-sign subtraction cannot overflow. Opposite signs need a
            // weighted sum instead, since their difference may exceed Num's range.
            center = sample.isNegative() == center.isNegative() ? center.plus(sample.minus(center).dividedBy(divisor))
                    : center.minus(center.dividedBy(divisor)).plus(sample.dividedBy(divisor));
        }
        if (!Num.isFinite(center)) {
            return null;
        }

        List<Num> widened = new ArrayList<>(samples.size());
        for (Num sample : samples) {
            Num scaled = MonteCarloArithmetic.affine(center, sample, factor, numFactory);
            if (scaled == null) {
                return null;
            }
            widened.add(scaled);
        }
        return widened;
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
        Num maximum = numFactory.zero();
        for (int i = from; i < window.size(); i++) {
            Num value = window.get(i);
            if (!Num.isFinite(value)) {
                return null;
            }
            Num magnitude = value.abs();
            if (magnitude.compareTo(maximum) > 0) {
                maximum = magnitude;
            }
            count++;
        }
        if (count < 2) {
            return null;
        }
        if (maximum.isZero()) {
            return maximum;
        }

        // Normalize before squaring. This keeps every square in [0, 1], so
        // finite DoubleNum windows above sqrt(MAX_VALUE) remain usable while
        // DecimalNum subnormal windows retain their high-precision magnitude.
        Num sumSquares = numFactory.zero();
        for (int i = from; i < window.size(); i++) {
            Num normalized = window.get(i).dividedBy(maximum);
            sumSquares = sumSquares.plus(normalized.multipliedBy(normalized));
        }
        Num normalizedRms = sumSquares.dividedBy(numFactory.numOf(count)).sqrt();
        return maximum.multipliedBy(normalizedRms);
    }

    @Override
    public String toString() {
        return "RecentVolatilityWideningMonteCarloMethod[recentBarCount=" + recentBarCount + ", maxWiden=" + maxWiden
                + ", inner=" + inner + "]";
    }
}
