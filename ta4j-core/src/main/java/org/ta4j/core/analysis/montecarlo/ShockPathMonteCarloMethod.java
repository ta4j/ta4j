/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.ShockModel;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.VolatilityUpdateMode;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Moment-driven recursive path simulation, the stock Monte Carlo technique.
 *
 * <p>
 * Each simulated path walks {@code horizon} bars where every step return equals
 * {@code drift + volatility * shock} (raw sampled return under
 * {@link ShockModel#HISTORICAL_BOOTSTRAP}), optionally updating variance by an
 * EWMA recursion after each step.
 *
 * <p>
 * This class reproduces the pre-0.24.2 built-in behavior exactly and remains
 * the default technique of both Monte Carlo forecast indicators.
 *
 * @since 0.24.2
 */
public final class ShockPathMonteCarloMethod implements MonteCarloMethod {

    private final ShockModel shockModel;
    private final VolatilityUpdateMode volatilityUpdateMode;
    private final double volatilityDecayFactor;

    /**
     * Creates a shock-path simulation configuration.
     *
     * @param shockModel            shock source for simulated paths
     * @param volatilityUpdateMode  variance update policy applied after each
     *                              simulated step
     * @param volatilityDecayFactor EWMA decay factor, required in {@code (0, 1)}
     *                              whenever the update mode is
     *                              {@link VolatilityUpdateMode#EWMA}; ignored but
     *                              still validated otherwise
     * @since 0.24.2
     */
    public ShockPathMonteCarloMethod(ShockModel shockModel, VolatilityUpdateMode volatilityUpdateMode,
            double volatilityDecayFactor) {
        this.shockModel = Objects.requireNonNull(shockModel, "shockModel must not be null");
        this.volatilityUpdateMode = Objects.requireNonNull(volatilityUpdateMode,
                "volatilityUpdateMode must not be null");
        if (Double.isNaN(volatilityDecayFactor) || volatilityDecayFactor <= 0d || volatilityDecayFactor >= 1d) {
            throw new IllegalArgumentException("volatilityDecayFactor must be in (0, 1)");
        }
        this.volatilityDecayFactor = volatilityDecayFactor;
    }

    /**
     * Samples shocks from the configured {@link ShockModel} and compounds them into
     * horizon cumulative log returns using the window's volatility state.
     *
     * @param context validated simulation inputs including the seeded random
     *                generator
     * @return exactly {@code context.iterationCount()} finite cumulative log-return
     *         samples, or {@code null} when the volatility state is unstable
     * @since 0.24.2
     */
    @Override
    public List<Num> terminalReturns(MonteCarloContext context) {
        ProjectionState state = ProjectionState.from(context.moments(), context.numFactory());
        if (state == null) {
            return null;
        }
        ShockSampler sampler = ShockSampler.create(shockModel, context.historicalLogReturns(), state,
                context.numFactory());
        boolean ewmaUpdate = volatilityUpdateMode == VolatilityUpdateMode.EWMA;
        NumFactory numFactory = context.numFactory();
        Num decay = ewmaUpdate ? numFactory.numOf(volatilityDecayFactor) : null;
        Num oneMinusDecay = ewmaUpdate ? numFactory.one().minus(decay) : null;

        List<Num> terminalReturns = new ArrayList<>(context.iterationCount());
        for (int iteration = 0; iteration < context.iterationCount(); iteration++) {
            Num cumulativeReturn = simulatePath(context, context.randomForPath(iteration), sampler, state, ewmaUpdate,
                    decay, oneMinusDecay);
            if (!Num.isFinite(cumulativeReturn)) {
                return null;
            }
            terminalReturns.add(cumulativeReturn);
        }
        return terminalReturns;
    }

    private Num simulatePath(MonteCarloContext context, RandomGenerator random, ShockSampler sampler,
            ProjectionState startingState, boolean ewmaUpdate, Num decay, Num oneMinusDecay) {
        NumFactory numFactory = context.numFactory();
        Num cumulativeReturn = numFactory.zero();
        Num drift = startingState.drift();
        Num mean = startingState.mean();
        Num variance = startingState.variance();
        Num volatility = startingState.volatility();
        for (int step = 0; step < context.horizon(); step++) {
            Num shock = sampler.sample(random);
            Num stepReturn = shockModel == ShockModel.HISTORICAL_BOOTSTRAP ? shock
                    : drift.plus(volatility.multipliedBy(shock));
            cumulativeReturn = cumulativeReturn.plus(stepReturn);
            if (ewmaUpdate) {
                Num deviation = stepReturn.minus(mean);
                mean = mean.multipliedBy(decay).plus(stepReturn.multipliedBy(oneMinusDecay));
                variance = variance.multipliedBy(decay)
                        .plus(deviation.multipliedBy(deviation).multipliedBy(oneMinusDecay));
                volatility = variance.isZero() ? numFactory.zero() : variance.sqrt();
            }
        }
        return cumulativeReturn;
    }

    private record ProjectionState(Num mean, Num drift, Num variance, Num volatility) {

        private static ProjectionState from(ReturnMoments moments, NumFactory numFactory) {
            if (moments == null || !moments.isStable() || moments.observationCount() <= 0) {
                return null;
            }
            Num mean = normalize(moments.mean(), numFactory);
            Num drift = normalize(moments.drift(), numFactory);
            Num variance = normalize(moments.variance(), numFactory);
            if (!Num.isFinite(mean) || !Num.isFinite(drift) || !Num.isFinite(variance) || variance.isNegative()) {
                return null;
            }
            Num volatility = variance.isZero() ? numFactory.zero() : variance.sqrt();
            return Num.isFinite(volatility) ? new ProjectionState(mean, drift, variance, volatility) : null;
        }

        private static Num normalize(Num value, NumFactory numFactory) {
            if (!Num.isFinite(value)) {
                return null;
            }
            Num normalized = numFactory.numOf(value.bigDecimalValue());
            return Num.isFinite(normalized) && (!normalized.isZero() || value.isZero()) ? normalized : null;
        }
    }

    @FunctionalInterface
    private interface ShockSampler {

        Num sample(RandomGenerator random);

        static ShockSampler create(ShockModel model, List<Num> historicalReturns, ProjectionState state,
                NumFactory numFactory) {
            return switch (model) {
            case HISTORICAL_BOOTSTRAP -> historicalBootstrap(historicalReturns);
            case STANDARDIZED_EMPIRICAL -> standardizedEmpirical(historicalReturns, state, numFactory);
            case SMOOTHED_EMPIRICAL -> smoothedEmpirical(historicalReturns, state, numFactory);
            case NORMAL -> random -> numFactory.numOf(random.nextGaussian());
            };
        }

        private static ShockSampler historicalBootstrap(List<Num> historicalReturns) {
            List<Num> samples = List.copyOf(historicalReturns);
            return random -> samples.get(random.nextInt(samples.size()));
        }

        private static ShockSampler standardizedEmpirical(List<Num> historicalReturns, ProjectionState state,
                NumFactory numFactory) {
            List<Num> shocks = standardize(historicalReturns, state);
            if (shocks == null) {
                return random -> numFactory.zero();
            }
            return random -> shocks.get(random.nextInt(shocks.size()));
        }

        private static ShockSampler smoothedEmpirical(List<Num> historicalReturns, ProjectionState state,
                NumFactory numFactory) {
            List<Num> shocks = standardize(historicalReturns, state);
            if (shocks == null) {
                return random -> numFactory.zero();
            }
            Num bandwidth = silvermanBandwidth(shocks, numFactory);
            if (bandwidth.isZero()) {
                return random -> shocks.get(random.nextInt(shocks.size()));
            }
            return random -> shocks.get(random.nextInt(shocks.size()))
                    .plus(numFactory.numOf(random.nextGaussian()).multipliedBy(bandwidth));
        }

        /**
         * Standardizes the historical window by the current estimated moments.
         *
         * @return standardized residuals, or {@code null} when the current volatility
         *         estimate collapses to zero
         */
        private static List<Num> standardize(List<Num> historicalReturns, ProjectionState state) {
            if (state.volatility().isZero()) {
                return null;
            }
            return historicalReturns.stream()
                    .map(value -> value.minus(state.mean()).dividedBy(state.volatility()))
                    .toList();
        }

        /**
         * Gaussian-kernel reference bandwidth {@code 1.06 * sd * n^(-1/5)} of the
         * standardized residual sample.
         *
         * @return positive bandwidth, or zero when the residual spread is degenerate
         *         (fewer than two observations or zero sample variance), which reduces
         *         smoothing to plain resampling
         */
        private static Num silvermanBandwidth(List<Num> shocks, NumFactory numFactory) {
            int count = shocks.size();
            if (count < 2) {
                return numFactory.zero();
            }
            Num sum = numFactory.zero();
            for (Num shock : shocks) {
                sum = sum.plus(shock);
            }
            Num mean = sum.dividedBy(numFactory.numOf(count));
            Num squaredDeviationSum = numFactory.zero();
            for (Num shock : shocks) {
                Num deviation = shock.minus(mean);
                squaredDeviationSum = squaredDeviationSum.plus(deviation.multipliedBy(deviation));
            }
            Num variance = squaredDeviationSum.dividedBy(numFactory.numOf(count - 1L));
            if (!Num.isFinite(variance) || variance.isZero() || variance.isNegative()) {
                return numFactory.zero();
            }
            Num standardDeviation = variance.sqrt();
            if (!Num.isFinite(standardDeviation)) {
                return numFactory.zero();
            }
            double multiplier = 1.06d * Math.pow(count, -0.2d);
            return standardDeviation.multipliedBy(numFactory.numOf(multiplier));
        }
    }
}
