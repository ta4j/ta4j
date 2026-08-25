/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.ShockModel;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.VolatilityUpdateMode;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Composition decorator that blends posterior parameter uncertainty with
 * kernel-smoothed residual paths.
 *
 * <p>
 * Each path draws {@code (sigmaSquared, mu)} from the shared
 * Normal-Inverse-Gamma posterior ({@link
 * NormalInverseGammaForecastMethod#posterior(MonteCarloContext)} and
 * {@link NormalInverseGammaForecastMethod#drawParameters}) and adds that
 * posterior scale and drift on top of the standardized kernel-smoothed residual
 * path produced by an inner {@link MonteCarloMethod}:
 *
 * <pre>
 * path = mu * horizon + sigma * standardizedResidualPath
 * </pre>
 *
 * <p>
 * Specifically, the inner technique is read with a constant volatility update
 * so its terminal path is {@code h * drift + volatility * shocks}; the
 * decorator recovers {@code shocks = (path - h*drift) / volatility}, the
 * standardized residual shape, and re-composes it at the nascent posterior
 * scale and drift. This is the composability claim of the seam: one technique's
 * parameter uncertainty decorates another's residual distribution without
 * either knowing the other.
 *
 * <p>
 * The decorator stresses the seam contract: it draws exclusively from
 * {@link MonteCarloContext#random()}, returns exactly
 * {@code context.iterationCount()} finite samples, propagates a {@code null}
 * (unstable) result from the inner method, and declares the forecast unstable
 * when the inner method returns the wrong sample count or the posterior cannot
 * be fitted.
 *
 * @see MonteCarloMethod
 * @see NormalInverseGammaForecastMethod
 * @since 0.24.2
 */
public final class PosteriorSmoothedResidualMonteCarloMethod implements MonteCarloMethod {

    private final MonteCarloMethod inner;
    private final NormalInverseGammaForecastMethod posteriorSource;

    /**
     * Wraps an inner technique that generates the kernel-smoothed residual path
     * shape. Defaults to plain kernel-smoothed standardized-empirical resampling
     * ({@link ShockModel#SMOOTHED_EMPIRICAL} with a constant volatility update).
     *
     * @param inner kernel-smoothed residual path generator, or {@code null} to use
     *              the default smoothed-empirical technique
     * @since 0.24.2
     */
    public PosteriorSmoothedResidualMonteCarloMethod(MonteCarloMethod inner) {
        this.inner = inner != null ? inner
                : new ShockPathMonteCarloMethod(ShockModel.SMOOTHED_EMPIRICAL, VolatilityUpdateMode.CONSTANT,
                                                0.5d);
        this.posteriorSource = NormalInverseGammaForecastMethod.withEmpiricalPriors();
    }

    /**
     * Composes one posterior parameter draw with the inner technique's
     * standardized residual path per iteration. See class Javadoc for the exact
     * transform.
     *
     * @param context validated simulation inputs including the seeded random
     *                generator
     * @return exactly {@code context.iterationCount()} finite cumulative log-return
     *         samples, or {@code null} when the posterior or the inner technique
     *         cannot produce a stable result
     * @since 0.24.2
     */
    @Override
    public List<Num> terminalReturns(MonteCarloContext context) {
        NormalInverseGammaForecastMethod.Posterior posterior = posteriorSource.posterior(context);
        if (posterior == null) {
            return null;
        }
        ReturnMoments moments = context.moments();
        if (moments == null || !moments.isStable() || moments.observationCount() <= 0) {
            return null;
        }
        NumFactory numFactory = context.numFactory();
        Num drift = normalize(moments.drift(), numFactory);
        Num variance = normalize(moments.variance(), numFactory);
        if (drift == null || variance == null || variance.isNegative()) {
            return null;
        }
        Num volatility = variance.isZero() ? numFactory.zero() : variance.sqrt();
        if (!Num.isFinite(volatility) || volatility.isZero()) {
            return null;
        }

        List<Num> innerSamples = inner.terminalReturns(context);
        if (innerSamples == null) {
            return null;
        }
        if (innerSamples.size() != context.iterationCount()) {
            return null;
        }

        RandomGenerator random = context.random();
        List<Num> terminalReturns = new ArrayList<>(context.iterationCount());
        for (int iteration = 0; iteration < context.iterationCount(); iteration++) {
            NormalInverseGammaForecastMethod.ParameterDraw draw = NormalInverseGammaForecastMethod
                    .drawParameters(posterior, random);
            double sigma = Math.sqrt(draw.sigmaSquared());
            Num residualPath = innerSamples.get(iteration)
                    .minus(drift.multipliedBy(numFactory.numOf(context.horizon())))
                    .dividedBy(volatility);
            double cumulativeReturn = draw.mu() * context.horizon() + sigma * residualPath.doubleValue();
            if (!Double.isFinite(cumulativeReturn)) {
                return null;
            }
            Num converted = numFactory.numOf(BigDecimal.valueOf(cumulativeReturn));
            if (!Num.isFinite(converted)) {
                return null;
            }
            terminalReturns.add(converted);
        }
        return terminalReturns;
    }

    private static Num normalize(Num value, NumFactory numFactory) {
        return Num.isFinite(value) ? numFactory.numOf(value.bigDecimalValue()) : null;
    }

    @Override
    public String toString() {
        return "PosteriorSmoothedResidualMonteCarloMethod[" + inner + "]";
    }
}
