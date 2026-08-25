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

    /** Posterior hyper-parameters {@code (mean, strength, shape, scale)}. */
    record Posterior(double mean, double strength, double shape, double scale) {
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
     * @return posterior hyper-parameters, or {@code null} when no stable posterior
     *         can be produced (empty window, non-finite window, or degenerate
     *         posterior scale)
     * @since 0.24.2
     */
    Posterior posterior(MonteCarloContext context) {
        List<Num> window = context.historicalLogReturns();
        int observationCount = window.size();
        if (observationCount == 0) {
            return null;
        }
        NumFactory numFactory = context.numFactory();
        Num sum = numFactory.zero();
        for (Num value : window) {
            if (!Num.isFinite(value)) {
                return null;
            }
            sum = sum.plus(value);
        }
        Num meanValue = sum.dividedBy(numFactory.numOf(observationCount));
        Num squaredDeviations = numFactory.zero();
        for (Num value : window) {
            Num deviation = value.minus(meanValue);
            squaredDeviations = squaredDeviations.plus(deviation.multipliedBy(deviation));
        }
        double windowMean = meanValue.doubleValue();
        double squaredDeviationSum = squaredDeviations.doubleValue();
        if (!Double.isFinite(windowMean) || !Double.isFinite(squaredDeviationSum)) {
            return null;
        }
        double sampleVariance = observationCount > 1 ? squaredDeviationSum / (observationCount - 1) : 0d;

        double strength = empiricalPriors ? 1d : priorStrength;
        double meanPrior = empiricalPriors ? windowMean : priorMean;
        double shape = empiricalPriors ? 2d : priorShape;
        double scale = empiricalPriors ? sampleVariance : priorScale;

        double posteriorStrength = strength + observationCount;
        double posteriorMean = (strength * meanPrior + observationCount * windowMean) / posteriorStrength;
        double posteriorShape = shape + observationCount / 2.0;
        double posteriorScale = scale + squaredDeviationSum / 2.0 + strength * observationCount
                * (windowMean - meanPrior) * (windowMean - meanPrior) / (2.0 * posteriorStrength);
        if (!isFinite(posteriorStrength, posteriorMean, posteriorShape, posteriorScale) || posteriorScale < 0d) {
            return null;
        }
        return new Posterior(posteriorMean, posteriorStrength, posteriorShape, posteriorScale);
    }

    /**
     * Draws posterior predictive parameters from the Normal-Inverse-Gamma posterior
     * fitted to the lookback window and compounds Normal increments into horizon
     * cumulative log returns. Posteriors whose mean or scale cannot cross to the
     * sampling precision degrade to an unstable result.
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

    private static double drawCumulativeReturn(Posterior posterior, MonteCarloContext context, RandomGenerator random) {
        ParameterDraw draw = drawParameters(posterior, random);
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
     * @return a single parameter draw
     * @since 0.24.2
     */
    static ParameterDraw drawParameters(Posterior posterior, RandomGenerator random) {
        double sigmaSquared = posterior.scale() == 0d ? 0d
                : nextInverseGamma(random, posterior.shape(), posterior.scale());
        double muDraw = posterior.mean() + Math.sqrt(sigmaSquared / posterior.strength()) * random.nextGaussian();
        return new ParameterDraw(sigmaSquared, muDraw);
    }

    private static double nextInverseGamma(RandomGenerator random, double shape, double rate) {
        return rate / nextGamma(random, shape);
    }

    private static double nextGamma(RandomGenerator random, double shape) {
        if (shape < 1d) {
            return nextGamma(random, shape + 1d) * Math.pow(random.nextDouble(), 1d / shape);
        }
        double delta = shape - 1d / 3d;
        double c = 1d / Math.sqrt(9d * delta);
        while (true) {
            double x;
            double v;
            do {
                x = random.nextGaussian();
                v = 1d + c * x;
            } while (v <= 0d);
            v = v * v * v;
            double u = random.nextDouble();
            if (u < 1d - 0.0331d * x * x * x * x) {
                return delta * v;
            }
            if (Math.log(u) < 0.5d * x * x + delta * (1d - v + Math.log(v))) {
                return delta * v;
            }
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static boolean isFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
