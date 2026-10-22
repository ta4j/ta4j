/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Posterior parameter uncertainty over another technique's residual shape,
 * behind
 * {@link NormalInverseGammaForecastMethod#overResiduals(MonteCarloMethod)}.
 *
 * <p>
 * The residual technique is read as constant-volatility paths
 * {@code h * drift + volatility * shocks}; each sample is standardized back to
 * {@code shocks} and re-composed with one posterior draw {@code (mu, sigma)}
 * from {@link NormalInverseGammaForecastMethod#posterior(MonteCarloContext)}
 * and {@link NormalInverseGammaForecastMethod#drawParameters}:
 *
 * <pre>
 * path = h * mu + sigma * (sample - h * drift) / volatility
 * </pre>
 *
 * The forecast is unstable when the residual technique breaks the seam contract
 * or the posterior cannot be fitted. A zero-scale posterior produces the
 * deterministic posterior-mean terminal return.
 */
final class PosteriorSmoothedResidualMonteCarloMethod implements MonteCarloMethod {

    private final NormalInverseGammaForecastMethod posteriorSource;
    private final MonteCarloMethod residualMethod;

    PosteriorSmoothedResidualMonteCarloMethod(NormalInverseGammaForecastMethod posteriorSource,
            MonteCarloMethod residualMethod) {
        this.posteriorSource = Objects.requireNonNull(posteriorSource, "posteriorSource");
        this.residualMethod = Objects.requireNonNull(residualMethod, "residualMethod");
    }

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
        Num drift = MonteCarloArithmetic.normalize(moments.drift(), numFactory);
        Num variance = MonteCarloArithmetic.normalize(moments.variance(), numFactory);
        if (drift == null || variance == null || variance.isNegative()) {
            return null;
        }
        Num volatility = variance.isZero() ? numFactory.zero() : variance.sqrt();
        if (!Num.isFinite(volatility)) {
            return null;
        }

        List<Num> residualSamples = MonteCarloArithmetic.normalizeSamples(residualMethod.terminalReturns(context),
                context);
        if (residualSamples == null) {
            return null;
        }
        if (posterior.scale().isZero()) {
            return deterministicPosteriorReturns(posterior, context);
        }
        if (volatility.isZero()) {
            return null;
        }
        RandomGenerator random = context.random();
        List<Num> terminalReturns = new ArrayList<>(residualSamples.size());
        Num horizon = numFactory.numOf(context.horizon());
        Num driftPath = drift.multipliedBy(horizon);
        for (Num residualSample : residualSamples) {
            NormalInverseGammaForecastMethod.ParameterDraw draw = NormalInverseGammaForecastMethod
                    .drawParameters(posterior, random);
            if (draw == null) {
                return null;
            }
            double sigma = Math.sqrt(draw.sigmaSquared());
            if (!Double.isFinite(draw.mu()) || !Double.isFinite(sigma)) {
                return null;
            }
            Num posteriorDrift = numFactory.numOf(BigDecimal.valueOf(draw.mu()));
            Num posteriorScale = numFactory.numOf(BigDecimal.valueOf(sigma));
            if (!Num.isFinite(posteriorDrift) || !Num.isFinite(posteriorScale)) {
                return null;
            }
            Num residualPath = residualSample.minus(driftPath).dividedBy(volatility);
            if (!Num.isFinite(residualPath)) {
                // Finite endpoints can overflow before division makes the
                // standardized difference representable. Narrow only afterwards.
                BigDecimal difference = residualSample.bigDecimalValue()
                        .subtract(drift.bigDecimalValue().multiply(BigDecimal.valueOf(context.horizon())));
                residualPath = numFactory
                        .numOf(difference.divide(volatility.bigDecimalValue(), MathContext.DECIMAL128));
            }
            Num cumulativeReturn = posteriorDrift.multipliedBy(horizon).plus(posteriorScale.multipliedBy(residualPath));
            if (!Num.isFinite(cumulativeReturn)) {
                return null;
            }
            terminalReturns.add(cumulativeReturn);
        }
        return terminalReturns;
    }

    private static List<Num> deterministicPosteriorReturns(NormalInverseGammaForecastMethod.Posterior posterior,
            MonteCarloContext context) {
        NumFactory numFactory = context.numFactory();
        Num posteriorMean = MonteCarloArithmetic.normalize(posterior.mean(), numFactory);
        if (posteriorMean == null) {
            return null;
        }
        Num cumulativeReturn = posteriorMean.multipliedBy(numFactory.numOf(context.horizon()));
        if (!Num.isFinite(cumulativeReturn)) {
            return null;
        }
        return Collections.nCopies(context.iterationCount(), cumulativeReturn);
    }

    @Override
    public String toString() {
        return "PosteriorSmoothedResidualMonteCarloMethod[posterior=" + posteriorSource + ", residuals="
                + residualMethod + "]";
    }
}
