/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Bayesian posterior-predictive Monte Carlo under Normal-Inverse-Gamma
 * conjugacy.
 *
 * <p>
 * The historical log-return window is modeled as independent draws from a
 * normal distribution with unknown mean {@code mu} and variance
 * {@code sigmaSquared}. Unlike plug-in schemes that treat estimated moments as
 * known constants, every simulated path first draws {@code (sigmaSquared, mu)}
 * from their conjugate posterior, so forecast quantiles widen coherently with
 * parameter estimation uncertainty. This matters most for short effective
 * samples such as weekly bars or windows shortly after a regime shift.
 *
 * <p>
 * Posterior updates given window statistics {@code n}, mean {@code rBar}, and
 * centered sum of squares {@code ssDev}:
 *
 * <pre>
 * kn = k0 + n
 * mn = (k0 * m0 + n * rBar) / kn
 * an = a0 + n / 2
 * bn = b0 + ssDev / 2 + k0 * n * (rBar - m0)^2 / (2 * kn)
 * </pre>
 *
 * where {@code (m0, k0, a0, b0)} are the prior hyper-parameters. Each path then
 * draws {@code sigmaSquared ~ InvGamma(an, bn)},
 * {@code mu | sigmaSquared ~ N(mn,
 * sigmaSquared / kn)}, and accumulates {@code horizon} steps of
 * {@code mu + sigma * z}.
 *
 * <p>
 * The posterior hyper-parameter computation is shared, package-private
 * {@link #posterior(MonteCarloContext)}, the single source of posterior draws
 * used by this method and {@link PosteriorSmoothedResidualMonteCarloMethod}.
 *
 * @see MonteCarloMethod
 * @since 0.24.2
 */
public final class NormalInverseGammaForecastMethod implements MonteCarloMethod {

    /**
     * Posterior hyper-parameters with active-domain and primitive sampling views.
     */
    record Posterior(Num mean, double strength, double shape, Num scale, double samplingMean, double samplingScale) {
    }

    /** One posterior parameter draw {@code (sigmaSquared, mu)}. */
    record ParameterDraw(double sigmaSquared, double mu) {
    }

    private final double priorMean;
    private final double priorStrength;
    private final double priorShape;
    private final double priorScale;
    private final boolean empiricalPriors;

    /**
     * Creates a method with explicit conjugate prior hyper-parameters.
     *
     * @param priorMean     prior mean {@code m0} of the return mean
     * @param priorStrength prior pseudo-observation count {@code k0 > 0} of the
     *                      return mean
     * @param priorShape    prior shape {@code a0 > 0} of the inverse-gamma variance
     * @param priorScale    prior scale {@code b0 >= 0} of the inverse-gamma
     *                      variance
     * @since 0.24.2
     */
    public NormalInverseGammaForecastMethod(double priorMean, double priorStrength, double priorShape,
            double priorScale) {
        requireFinite(priorMean, "priorMean");
        requireFinite(priorStrength, "priorStrength");
        requireFinite(priorShape, "priorShape");
        requireFinite(priorScale, "priorScale");
        if (priorStrength <= 0d) {
            throw new IllegalArgumentException("priorStrength must be > 0");
        }
        if (priorShape <= 0d) {
            throw new IllegalArgumentException("priorShape must be > 0");
        }
        if (priorScale < 0d) {
            throw new IllegalArgumentException("priorScale must be >= 0");
        }
        this.priorMean = priorMean;
        this.priorStrength = priorStrength;
        this.priorShape = priorShape;
        this.priorScale = priorScale;
        this.empiricalPriors = false;
    }

    private NormalInverseGammaForecastMethod() {
        this.priorMean = 0d;
        this.priorStrength = 0d;
        this.priorShape = 0d;
        this.priorScale = 0d;
        this.empiricalPriors = true;
    }

    /**
     * Creates a method with weakly-informative data-driven priors: the prior mean
     * centers on the window mean with one pseudo-observation ({@code k0 = 1}), and
     * the variance prior carries two pseudo-observations at the sample variance
     * ({@code a0 = 2, b0 = s^2}). Realistic windows dominate these priors while
     * they keep every posterior proper.
     *
     * @return method with empirical priors
     * @since 0.24.2
     */
    public static NormalInverseGammaForecastMethod withEmpiricalPriors() {
        return new NormalInverseGammaForecastMethod();
    }

    /**
     * Computes the Normal-Inverse-Gamma posterior hyper-parameters for the context
     * window, shared as the single source of posterior draws between this method
     * and {@link PosteriorSmoothedResidualMonteCarloMethod}.
     *
     * @param context validated simulation inputs
     * @return posterior hyper-parameters, or {@code null} when the window is empty
     *         or non-finite, the posterior scale is invalid, or a nonzero
     *         stochastic posterior parameter cannot reach primitive sampling
     *         precision
     * @since 0.25.1
     */
    Posterior posterior(MonteCarloContext context) {
        List<Num> window = context.historicalLogReturns();
        int observationCount = window.size();
        if (observationCount == 0) {
            return null;
        }
        NumFactory numFactory = context.numFactory();
        Num observationCountValue = numFactory.numOf(observationCount);
        Num sum = numFactory.zero();
        for (Num value : window) {
            if (!Num.isFinite(value)) {
                return null;
            }
            sum = sum.plus(value);
        }
        Num meanValue = sum.dividedBy(observationCountValue);
        Num squaredDeviations = numFactory.zero();
        for (Num value : window) {
            Num deviation = value.minus(meanValue);
            squaredDeviations = squaredDeviations.plus(deviation.multipliedBy(deviation));
        }
        Num sampleVariance = observationCount > 1 ? squaredDeviations.dividedBy(numFactory.numOf(observationCount - 1))
                : numFactory.zero();

        double strength = empiricalPriors ? 1d : priorStrength;
        double shape = empiricalPriors ? 2d : priorShape;
        double posteriorStrength = strength + observationCount;
        double posteriorShape = shape + observationCount / 2.0;
        if (!Double.isFinite(posteriorStrength) || !Double.isFinite(posteriorShape)) {
            return null;
        }
        Num meanPrior = empiricalPriors ? meanValue : numFactory.numOf(priorMean);
        Num scale = empiricalPriors ? sampleVariance : numFactory.numOf(priorScale);
        Num strengthValue = numFactory.numOf(strength);
        Num posteriorStrengthValue = numFactory.numOf(posteriorStrength);
        Num posteriorMean = strengthValue.multipliedBy(meanPrior)
                .plus(observationCountValue.multipliedBy(meanValue))
                .dividedBy(posteriorStrengthValue);
        Num meanDifference = meanValue.minus(meanPrior);
        Num posteriorScale = scale.plus(squaredDeviations.dividedBy(numFactory.two()))
                .plus(strengthValue.multipliedBy(observationCountValue)
                        .multipliedBy(meanDifference)
                        .multipliedBy(meanDifference)
                        .dividedBy(numFactory.two().multipliedBy(posteriorStrengthValue)));
        if (!Num.isFinite(posteriorMean) || !Num.isFinite(posteriorScale) || posteriorScale.isNegative()) {
            return null;
        }
        double samplingMean = samplingDouble(posteriorMean);
        double samplingScale = samplingDouble(posteriorScale);
        // Gamma and Gaussian draws require primitive doubles. Preserve exact
        // zero-scale paths in the active Num domain.
        if (!posteriorScale.isZero() && (!Double.isFinite(samplingMean) || !Double.isFinite(samplingScale))) {
            return null;
        }
        return new Posterior(posteriorMean, posteriorStrength, posteriorShape, posteriorScale, samplingMean,
                samplingScale);
    }

    /**
     * Draws posterior predictive parameters from the Normal-Inverse-Gamma posterior
     * fitted to the lookback window and compounds Normal increments into horizon
     * cumulative log returns. A zero-scale posterior stays entirely in the active
     * {@link Num} domain. Nonzero posterior parameters that cannot reach sampling
     * precision degrade to an unstable result.
     *
     * @param context validated simulation inputs including the seeded random
     *                generator
     * @return exactly {@code context.iterationCount()} finite cumulative log-return
     *         samples, or {@code null} when no stable posterior can be produced
     * @since 0.24.2
     */
    @Override
    public List<Num> terminalReturns(MonteCarloContext context) {
        Posterior posterior = posterior(context);
        if (posterior == null) {
            return null;
        }
        if (posterior.scale().isZero()) {
            return deterministicCumulativeReturns(posterior.mean(), context);
        }
        RandomGenerator random = context.random();
        List<Num> terminalReturns = new ArrayList<>(context.iterationCount());
        for (int iteration = 0; iteration < context.iterationCount(); iteration++) {
            double cumulativeReturn = drawCumulativeReturn(posterior, context, random);
            if (!Double.isFinite(cumulativeReturn)) {
                return null;
            }
            Num converted = context.numFactory().numOf(BigDecimal.valueOf(cumulativeReturn));
            if (!Num.isFinite(converted)) {
                return null;
            }
            terminalReturns.add(converted);
        }
        return terminalReturns;
    }

    private static List<Num> deterministicCumulativeReturns(Num mean, MonteCarloContext context) {
        NumFactory numFactory = context.numFactory();
        Num normalizedMean = MonteCarloArithmetic.normalize(mean, numFactory);
        if (normalizedMean == null) {
            return null;
        }
        Num cumulativeReturn = normalizedMean.multipliedBy(numFactory.numOf(context.horizon()));
        if (!Num.isFinite(cumulativeReturn)) {
            return null;
        }
        List<Num> terminalReturns = new ArrayList<>(context.iterationCount());
        for (int iteration = 0; iteration < context.iterationCount(); iteration++) {
            terminalReturns.add(cumulativeReturn);
        }
        return terminalReturns;
    }

    private static double drawCumulativeReturn(Posterior posterior, MonteCarloContext context, RandomGenerator random) {
        ParameterDraw draw = drawParameters(posterior, random);
        if (draw == null) {
            return Double.NaN;
        }
        double sigma = Math.sqrt(draw.sigmaSquared());
        double cumulativeReturn = 0d;
        for (int step = 0; step < context.horizon(); step++) {
            cumulativeReturn += draw.mu() + sigma * random.nextGaussian();
        }
        return cumulativeReturn;
    }

    /**
     * Draws {@code (sigmaSquared, mu)} from the posterior predictive conditional,
     * shared as the single source of parameter draws between this method and
     * {@link PosteriorSmoothedResidualMonteCarloMethod} for identical seeds and
     * windows.
     *
     * @param posterior fitted posterior hyper-parameters
     * @param random    deterministic seeded random generator
     * @return a single parameter draw, or {@code null} when the posterior cannot
     *         reach primitive sampling precision
     * @since 0.25.1
     */
    static ParameterDraw drawParameters(Posterior posterior, RandomGenerator random) {
        double mean = posterior.samplingMean();
        double scale = posterior.samplingScale();
        if (!Double.isFinite(mean) || !Double.isFinite(scale)) {
            return null;
        }
        double sigmaSquared = scale == 0d ? 0d : nextInverseGamma(random, posterior.shape(), scale);
        if (!Double.isFinite(sigmaSquared)) {
            return null;
        }
        double muDraw = mean + Math.sqrt(sigmaSquared / posterior.strength()) * random.nextGaussian();
        return Double.isFinite(muDraw) ? new ParameterDraw(sigmaSquared, muDraw) : null;
    }

    private static double nextInverseGamma(RandomGenerator random, double shape, double rate) {
        return rate / RandomSamplers.nextGamma(random, shape);
    }

    // Primitive gamma/Gaussian sampling must not erase a finite nonzero Num.
    private static double samplingDouble(Num value) {
        double primitive = value.doubleValue();
        return Double.isFinite(primitive) && (primitive != 0d || value.isZero()) ? primitive : Double.NaN;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

}
