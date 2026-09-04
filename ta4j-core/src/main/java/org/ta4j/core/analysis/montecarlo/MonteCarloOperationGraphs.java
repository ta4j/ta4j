/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.util.List;

/**
 * Package-private canonical description and lowering path for the explicitly
 * supported built-in {@link MonteCarloMethod} graphs.
 *
 * <p>
 * {@link #lower(MonteCarloMethod)} walks a method graph and either returns a
 * {@link MonteCarloOperation} tree whose semantics are completely known, or
 * {@code null} when the graph contains any custom lambda, third-party
 * implementation, or unknown decorator. Declining is the contract: an
 * unsupported graph remains fully valid on the scalar path and must simply
 * never be offered to a native provider, so no provider discovery or
 * initialization happens for it.
 *
 * <p>
 * The lowering matches only the built-in techniques in this package
 * ({@link ShockPathMonteCarloMethod}, {@link NormalInverseGammaForecastMethod},
 * and the four composition decorators). Everything else - including any future
 * user-written technique - declines by construction, without reflection or
 * probing.
 *
 * <p>
 * <b>Versioning contract.</b> Each operation type carries an integer version
 * that binds the node's observable deterministic semantics, including its
 * random-number consumption pattern. The following are part of the version and
 * require a version bump - and therefore a new operation identity - when they
 * change:
 *
 * <ul>
 * <li>{@code shockPath} v1: per iteration the shock sampler draws
 * {@code horizon} shocks; {@code NORMAL} consumes one {@code nextGaussian()}
 * per shock while the empirical samplers consume one {@code nextInt(bound)}.
 * The EWMA update adds no draws.</li>
 * <li>{@code normalInverseGamma} v1: per iteration one parameter draw (one
 * inverse-gamma via the Marsaglia-Tsang rejection sampler plus one
 * {@code nextGaussian()}) followed by {@code horizon} standard-normal
 * increments.</li>
 * <li>{@code studentTScaleMixing} v1: per iteration one chi-square scale draw
 * from {@code Gamma(df/2)} via the shared rejection sampler, consuming a
 * variable but shape-independent draw count.</li>
 * <li>{@code recentVolatilityWidening} v1: consumes nothing itself; only the
 * child's draws.</li>
 * <li>{@code posteriorSmoothedResidual} v1: per iteration one parameter draw
 * with the same pattern as {@code normalInverseGamma} plus the child's
 * draws.</li>
 * <li>{@code ensemble} v1: per invocation two {@code nextLong()} draws seed one
 * {@code SplittableRandom} per child; the iteration budget splits with the
 * first child receiving {@code iterationCount / 2} and the second the
 * remainder, and the pooled samples concatenate first-then-second. Split rule,
 * stream derivation, and concatenation order are all version-bound.</li>
 * </ul>
 *
 * <p>
 * This surface is internal on purpose: it seeds the operation-level ABI for
 * native acceleration and stays package-private until at least two structurally
 * different accelerated operations prove it.
 */
final class MonteCarloOperationGraphs {

    /** Operation type key of {@link ShockPathMonteCarloMethod}. */
    static final String TYPE_SHOCK_PATH = "shockPath";

    /** Operation type key of {@link NormalInverseGammaForecastMethod}. */
    static final String TYPE_NORMAL_INVERSE_GAMMA = "normalInverseGamma";

    /** Operation type key of {@link StudentTScaleMixingMonteCarloMethod}. */
    static final String TYPE_STUDENT_T_SCALE_MIXING = "studentTScaleMixing";

    /** Operation type key of {@link RecentVolatilityWideningMonteCarloMethod}. */
    static final String TYPE_RECENT_VOLATILITY_WIDENING = "recentVolatilityWidening";

    /** Operation type key of {@link PosteriorSmoothedResidualMonteCarloMethod}. */
    static final String TYPE_POSTERIOR_SMOOTHED_RESIDUAL = "posteriorSmoothedResidual";

    /** Operation type key of {@link EnsembleMonteCarloMethod}. */
    static final String TYPE_ENSEMBLE = "ensemble";

    /** First and only operation version per built-in type (see class Javadoc). */
    static final int VERSION_1 = 1;

    private MonteCarloOperationGraphs() {
    }

    /**
     * Lowers a method graph into its canonical operation description.
     *
     * @param method the method graph to describe, not {@code null}
     * @return the canonical description, or {@code null} when the graph's semantics
     *         are not completely known (custom lambda, third-party implementation,
     *         or unknown/unsupported nesting)
     */
    static MonteCarloOperation lower(MonteCarloMethod method) {
        if (method instanceof ShockPathMonteCarloMethod shockPath) {
            return new MonteCarloOperation(TYPE_SHOCK_PATH, VERSION_1, List.of(shockPath.shockModel().name(),
                    shockPath.volatilityUpdateMode().name(), shockPath.volatilityDecayFactor()), List.of());
        }
        if (method instanceof NormalInverseGammaForecastMethod normalInverseGamma) {
            return new MonteCarloOperation(TYPE_NORMAL_INVERSE_GAMMA, VERSION_1,
                    List.of(normalInverseGamma.priorMean(), normalInverseGamma.priorStrength(),
                            normalInverseGamma.priorShape(), normalInverseGamma.priorScale(),
                            normalInverseGamma.empiricalPriors()),
                    List.of());
        }
        if (method instanceof StudentTScaleMixingMonteCarloMethod studentT) {
            MonteCarloOperation child = lower(studentT.inner());
            return child == null ? null
                    : new MonteCarloOperation(TYPE_STUDENT_T_SCALE_MIXING, VERSION_1,
                            List.of(studentT.degreesOfFreedom()), List.of(child));
        }
        if (method instanceof RecentVolatilityWideningMonteCarloMethod widening) {
            MonteCarloOperation child = lower(widening.inner());
            return child == null ? null
                    : new MonteCarloOperation(TYPE_RECENT_VOLATILITY_WIDENING, VERSION_1,
                            List.of(widening.recentBarCount(), widening.maxWiden()), List.of(child));
        }
        if (method instanceof PosteriorSmoothedResidualMonteCarloMethod smoothedResidual) {
            MonteCarloOperation child = lower(smoothedResidual.inner());
            return child == null ? null
                    : new MonteCarloOperation(TYPE_POSTERIOR_SMOOTHED_RESIDUAL, VERSION_1, List.of(), List.of(child));
        }
        if (method instanceof EnsembleMonteCarloMethod ensemble) {
            MonteCarloOperation first = lower(ensemble.first());
            MonteCarloOperation second = lower(ensemble.second());
            return first == null || second == null ? null
                    : new MonteCarloOperation(TYPE_ENSEMBLE, VERSION_1, List.of(), List.of(first, second));
        }
        return null;
    }
}
