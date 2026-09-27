/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Computes a fully invested long-only minimum-variance allocation.
 *
 * <p>
 * The optimizer estimates the covariance matrix of aligned one-bar simple
 * returns and minimizes portfolio variance on the bounded probability simplex.
 * It does not estimate expected returns. The optional maximum asset weight
 * provides a concentration constraint without changing the long-only, fully
 * invested contract.
 * </p>
 *
 * <p>
 * Covariance is estimated with Ledoit-Wolf shrinkage by default. The raw sample
 * covariance is noisy when there are few observations per asset, and a
 * minimum-variance optimizer concentrates on whichever assets it
 * underestimates; shrinkage yields more stable, better diversified weights out
 * of sample. {@link CovarianceEstimator#SAMPLE} is available through
 * {@link #withCovarianceEstimator(CovarianceEstimator)}:
 * </p>
 *
 * <pre>{@code
 * PortfolioAllocation capped = new MinimumVarianceOptimizer(series, 0.25).optimize();
 * PortfolioAllocation sample = new MinimumVarianceOptimizer(series).withCovarianceEstimator(CovarianceEstimator.SAMPLE)
 *         .optimize();
 * }</pre>
 *
 * <p>
 * Calculations use the portfolio series' {@link NumFactory}. A deterministic
 * projected-gradient solver avoids matrix inversion, so singular covariance
 * matrices are supported.
 * </p>
 *
 * @since 0.25.1
 */
public final class MinimumVarianceOptimizer {

    private static final int MAX_ITERATIONS = 20_000;

    private final PortfolioSeries series;
    private final int index;
    private final int barCount;
    private final Num maximumAssetWeight;
    private final CovarianceEstimator covarianceEstimator;

    /**
     * How the optimizer estimates the return covariance matrix.
     *
     * @since 0.25.1
     */
    public enum CovarianceEstimator {

        /**
         * Population sample covariance of the window's simple returns. Unbiased in
         * large samples, but noisy when the window is short relative to the asset
         * count.
         *
         * @since 0.25.1
         */
        SAMPLE,

        /**
         * Ledoit-Wolf (2004) shrinkage of the sample covariance toward {@code mu * I},
         * where {@code mu} is the average sample variance. The shrinkage intensity is
         * estimated from the data to minimize expected squared error, matching
         * scikit-learn's {@code LedoitWolf} estimator. This is the default.
         *
         * @since 0.25.1
         */
        LEDOIT_WOLF
    }

    /**
     * Creates an uncapped optimizer over all available simple returns.
     *
     * @param series aligned portfolio series
     * @since 0.25.1
     */
    public MinimumVarianceOptimizer(PortfolioSeries series) {
        this(series, Objects.requireNonNull(series, "series").getEndIndex(), series.getEndIndex(), 1);
    }

    /**
     * Creates a capped optimizer over all available simple returns.
     *
     * @param series             aligned portfolio series
     * @param maximumAssetWeight maximum weight for any asset, in {@code (0, 1]},
     *                           for example {@code 0.25}
     * @since 0.25.1
     */
    public MinimumVarianceOptimizer(PortfolioSeries series, Number maximumAssetWeight) {
        this(series, Objects.requireNonNull(series, "series").getEndIndex(), series.getEndIndex(), maximumAssetWeight);
    }

    /**
     * Creates an uncapped optimizer over an explicit historical return window.
     *
     * @param series   aligned portfolio series
     * @param index    final aligned index included in estimation
     * @param barCount number of one-bar return observations
     * @since 0.25.1
     */
    public MinimumVarianceOptimizer(PortfolioSeries series, int index, int barCount) {
        this(series, index, barCount, 1);
    }

    /**
     * Creates a capped optimizer over an explicit historical return window.
     *
     * @param series             aligned portfolio series
     * @param index              final aligned index included in estimation
     * @param barCount           number of one-bar return observations
     * @param maximumAssetWeight maximum weight for any asset, in {@code (0, 1]},
     *                           for example {@code 0.25}
     * @since 0.25.1
     */
    public MinimumVarianceOptimizer(PortfolioSeries series, int index, int barCount, Number maximumAssetWeight) {
        this.series = Objects.requireNonNull(series, "series");
        if (index < series.getBeginIndex() || index > series.getEndIndex()) {
            throw new IndexOutOfBoundsException(
                    "index must be between " + series.getBeginIndex() + " and " + series.getEndIndex());
        }
        if (barCount < 2 || barCount > index - series.getBeginIndex()) {
            throw new IllegalArgumentException("barCount must be between 2 and the available return count");
        }
        this.index = index;
        this.barCount = barCount;
        this.maximumAssetWeight = normalizeMaximumWeight(maximumAssetWeight);
        this.covarianceEstimator = CovarianceEstimator.LEDOIT_WOLF;
    }

    private MinimumVarianceOptimizer(MinimumVarianceOptimizer source, CovarianceEstimator covarianceEstimator) {
        this.series = source.series;
        this.index = source.index;
        this.barCount = source.barCount;
        this.maximumAssetWeight = source.maximumAssetWeight;
        this.covarianceEstimator = covarianceEstimator;
    }

    /**
     * Returns an optimizer with the same window and cap that estimates covariance
     * with {@code estimator}. The default is
     * {@link CovarianceEstimator#LEDOIT_WOLF}.
     *
     * @param estimator covariance estimator
     * @return optimizer using {@code estimator}
     * @since 0.25.1
     */
    public MinimumVarianceOptimizer withCovarianceEstimator(CovarianceEstimator estimator) {
        return new MinimumVarianceOptimizer(this, Objects.requireNonNull(estimator, "estimator"));
    }

    /**
     * Computes the minimum-variance allocation.
     *
     * @return fully invested long-only allocation
     * @throws IllegalArgumentException if the estimation window contains invalid
     *                                  prices or returns
     * @throws IllegalStateException    if the numerical solver does not converge
     * @since 0.25.1
     */
    public PortfolioAllocation optimize() {
        Num[] weights = minimize(covarianceMatrix());
        List<String> assets = series.getAssets();
        Map<String, BigDecimal> targetWeights = new LinkedHashMap<>();
        for (int assetIndex = 0; assetIndex < assets.size(); assetIndex++) {
            targetWeights.put(assets.get(assetIndex), weights[assetIndex].bigDecimalValue());
        }
        return new PortfolioAllocation(targetWeights);
    }

    private Num normalizeMaximumWeight(Number maximumWeight) {
        Objects.requireNonNull(maximumWeight, "maximumAssetWeight");
        Num normalized;
        try {
            normalized = series.numFactory().numOf(maximumWeight);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("maximumAssetWeight must be finite and in (0, 1]", exception);
        }
        Num one = series.numFactory().one();
        if (!Num.isFinite(normalized) || normalized.isNegativeOrZero() || normalized.isGreaterThan(one)) {
            throw new IllegalArgumentException("maximumAssetWeight must be finite and in (0, 1]");
        }
        Num assetCount = series.numFactory().numOf(series.getAssets().size());
        if (normalized.multipliedBy(assetCount).isLessThan(one)) {
            throw new IllegalArgumentException("maximumAssetWeight is infeasible for the portfolio asset count");
        }
        return normalized;
    }

    /**
     * Covariance of one-bar simple returns over the window: the population sample
     * covariance, shrunk when the estimator is
     * {@link CovarianceEstimator#LEDOIT_WOLF}.
     */
    Num[][] covarianceMatrix() {
        List<String> assets = series.getAssets();
        int assetCount = assets.size();
        NumFactory numFactory = series.numFactory();
        Num[][] deviations = new Num[barCount][assetCount];
        int firstReturnIndex = index - barCount + 1;
        Num observationCount = numFactory.numOf(barCount);

        for (int assetIndex = 0; assetIndex < assetCount; assetIndex++) {
            String asset = assets.get(assetIndex);
            Num sum = numFactory.zero();
            Num previous = requirePositive(series.getClosePrice(asset, firstReturnIndex - 1), asset,
                    firstReturnIndex - 1);
            for (int observation = 0; observation < barCount; observation++) {
                int currentIndex = firstReturnIndex + observation;
                Num current = requirePositive(series.getClosePrice(asset, currentIndex), asset, currentIndex);
                Num value = current.dividedBy(previous).minus(numFactory.one());
                if (!Num.isFinite(value)) {
                    throw new IllegalArgumentException(
                            "simple return must be finite for asset " + asset + " at index " + currentIndex);
                }
                deviations[observation][assetIndex] = value;
                sum = sum.plus(value);
                previous = current;
            }
            // Center once so the covariance loop needs one multiply-add per term.
            Num mean = sum.dividedBy(observationCount);
            for (Num[] observation : deviations) {
                observation[assetIndex] = observation[assetIndex].minus(mean);
            }
        }

        Num[][] covariance = new Num[assetCount][assetCount];
        for (int row = 0; row < assetCount; row++) {
            for (int column = row; column < assetCount; column++) {
                Num sum = numFactory.zero();
                for (Num[] observation : deviations) {
                    sum = sum.plus(observation[row].multipliedBy(observation[column]));
                }
                Num value = sum.dividedBy(observationCount);
                if (!Num.isFinite(value)) {
                    throw new IllegalArgumentException("covariance matrix must contain only finite values");
                }
                covariance[row][column] = value;
                covariance[column][row] = value;
            }
        }
        if (covarianceEstimator == CovarianceEstimator.LEDOIT_WOLF) {
            shrinkTowardScaledIdentity(covariance, deviations);
        }
        return covariance;
    }

    /**
     * Ledoit and Wolf (2004), "A well-conditioned estimator for large-dimensional
     * covariance matrices". With p assets, n centered observations x_k, sample
     * covariance S and mu = trace(S) / p, the estimated intensity is min(beta,
     * delta) / delta, where delta = ||S - mu I||^2 / p and beta = (sum_k ||x_k||^4
     * / n - ||S||^2) / (p n), using Frobenius norms.
     */
    private void shrinkTowardScaledIdentity(Num[][] covariance, Num[][] deviations) {
        NumFactory numFactory = series.numFactory();
        int assetCount = covariance.length;
        Num assets = numFactory.numOf(assetCount);
        Num observations = numFactory.numOf(deviations.length);

        Num trace = numFactory.zero();
        Num squaredNorm = numFactory.zero();
        for (int row = 0; row < assetCount; row++) {
            trace = trace.plus(covariance[row][row]);
            for (int column = 0; column < assetCount; column++) {
                squaredNorm = squaredNorm.plus(covariance[row][column].multipliedBy(covariance[row][column]));
            }
        }
        Num fourthMoment = numFactory.zero();
        for (Num[] observation : deviations) {
            Num observationNorm = numFactory.zero();
            for (Num value : observation) {
                observationNorm = observationNorm.plus(value.multipliedBy(value));
            }
            fourthMoment = fourthMoment.plus(observationNorm.multipliedBy(observationNorm));
        }

        // ||S - mu I||^2 = ||S||^2 - trace(S)^2 / p.
        Num delta = squaredNorm.minus(trace.multipliedBy(trace).dividedBy(assets)).dividedBy(assets);
        if (!delta.isPositive()) {
            return; // S is already a multiple of the identity.
        }
        Num beta = fourthMoment.dividedBy(observations)
                .minus(squaredNorm)
                .dividedBy(assets.multipliedBy(observations))
                .max(numFactory.zero())
                .min(delta);
        Num shrinkage = beta.dividedBy(delta);
        Num retained = numFactory.one().minus(shrinkage);
        Num target = shrinkage.multipliedBy(trace.dividedBy(assets));
        for (int row = 0; row < assetCount; row++) {
            for (int column = 0; column < assetCount; column++) {
                covariance[row][column] = covariance[row][column].multipliedBy(retained);
            }
            covariance[row][row] = covariance[row][row].plus(target);
        }
    }

    /**
     * Accelerated projected gradient (FISTA) with gradient-based adaptive restart
     * (O'Donoghue and Candes, 2015). The restart drops stale momentum whenever it
     * points uphill, which keeps convergence linear on ill-conditioned covariance
     * matrices instead of oscillating.
     */
    private Num[] minimize(Num[][] covariance) {
        NumFactory numFactory = series.numFactory();
        int assetCount = covariance.length;
        Num one = numFactory.one();
        Num two = numFactory.two();
        Num four = two.multipliedBy(two);
        Num tolerance = numFactory.epsilon();
        Num lipschitzBound = lipschitzBound(covariance);
        if (!Num.isFinite(lipschitzBound)) {
            throw new IllegalArgumentException("covariance matrix must contain only finite values");
        }
        Num[] weights = new Num[assetCount];
        Arrays.fill(weights, one.dividedBy(numFactory.numOf(assetCount)));
        if (lipschitzBound.isZero()) {
            return weights;
        }

        // The gradient of w'Cw is 2Cw; folding the 2 into the step saves a multiply.
        Num step = two.dividedBy(lipschitzBound);
        Num[] accelerated = weights.clone();
        Num acceleration = one;
        Num[] unprojected = new Num[assetCount];

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            for (int row = 0; row < assetCount; row++) {
                Num product = numFactory.zero();
                for (int column = 0; column < assetCount; column++) {
                    product = product.plus(covariance[row][column].multipliedBy(accelerated[column]));
                }
                unprojected[row] = accelerated[row].minus(step.multipliedBy(product));
            }
            Num[] nextWeights = project(unprojected);

            Num maximumChange = numFactory.zero();
            Num momentumAlignment = numFactory.zero();
            for (int assetIndex = 0; assetIndex < assetCount; assetIndex++) {
                Num change = nextWeights[assetIndex].minus(weights[assetIndex]);
                maximumChange = maximumChange.max(change.abs());
                momentumAlignment = momentumAlignment
                        .plus(accelerated[assetIndex].minus(nextWeights[assetIndex]).multipliedBy(change));
            }
            if (maximumChange.isLessThanOrEqual(tolerance)) {
                return nextWeights;
            }

            if (momentumAlignment.isPositive()) {
                acceleration = one;
                accelerated = nextWeights.clone();
            } else {
                Num nextAcceleration = one
                        .plus(one.plus(four.multipliedBy(acceleration.multipliedBy(acceleration))).sqrt())
                        .dividedBy(two);
                Num momentum = acceleration.minus(one).dividedBy(nextAcceleration);
                for (int assetIndex = 0; assetIndex < assetCount; assetIndex++) {
                    Num change = nextWeights[assetIndex].minus(weights[assetIndex]);
                    accelerated[assetIndex] = nextWeights[assetIndex].plus(momentum.multipliedBy(change));
                }
                acceleration = nextAcceleration;
            }
            weights = nextWeights;
        }

        throw new IllegalStateException("minimum-variance optimization did not converge");
    }

    /**
     * Gershgorin bound: twice the largest absolute row sum bounds 2 * lambdaMax.
     */
    private Num lipschitzBound(Num[][] covariance) {
        Num maxRowSum = series.numFactory().zero();
        for (Num[] row : covariance) {
            Num rowSum = series.numFactory().zero();
            for (Num value : row) {
                rowSum = rowSum.plus(value.abs());
            }
            maxRowSum = maxRowSum.max(rowSum);
        }
        return series.numFactory().two().multipliedBy(maxRowSum);
    }

    /**
     * Exact Euclidean projection onto {@code {w : sum w = 1, 0 <= w <= cap}}. The
     * projection is {@code clamp(v - tau)} for the threshold {@code tau} where the
     * piecewise-linear, non-increasing {@code sum clamp(v - tau)} equals one; it is
     * located between two sorted breakpoints and solved linearly there.
     */
    private Num[] project(Num[] values) {
        NumFactory numFactory = series.numFactory();
        Num one = numFactory.one();
        Num[] breakpoints = new Num[values.length * 2];
        for (int assetIndex = 0; assetIndex < values.length; assetIndex++) {
            Num value = values[assetIndex];
            if (!Num.isFinite(value)) {
                throw new IllegalStateException("minimum-variance optimization did not converge");
            }
            breakpoints[2 * assetIndex] = value.minus(maximumAssetWeight);
            breakpoints[2 * assetIndex + 1] = value;
        }
        Arrays.sort(breakpoints);

        // Sum at the lowest breakpoint is assetCount * cap >= 1 and at the highest it
        // is 0, so the root lies between breakpoints[low] and breakpoints[low + 1].
        int low = 0;
        int high = breakpoints.length - 1;
        while (high - low > 1) {
            int middle = (low + high) >>> 1;
            if (clampedSum(values, breakpoints[middle]).isGreaterThanOrEqual(one)) {
                low = middle;
            } else {
                high = middle;
            }
        }
        Num lowSum = clampedSum(values, breakpoints[low]);
        Num highSum = clampedSum(values, breakpoints[high]);
        Num threshold = breakpoints[low];
        if (lowSum.isGreaterThan(highSum)) {
            threshold = threshold.plus(lowSum.minus(one)
                    .dividedBy(lowSum.minus(highSum))
                    .multipliedBy(breakpoints[high].minus(breakpoints[low])));
        }

        Num[] projected = new Num[values.length];
        Num sum = numFactory.zero();
        for (int assetIndex = 0; assetIndex < values.length; assetIndex++) {
            projected[assetIndex] = clamp(values[assetIndex].minus(threshold));
            sum = sum.plus(projected[assetIndex]);
        }
        correctProjectionResidual(projected, one.minus(sum));
        return projected;
    }

    private Num clampedSum(Num[] values, Num threshold) {
        Num sum = series.numFactory().zero();
        for (Num value : values) {
            sum = sum.plus(clamp(value.minus(threshold)));
        }
        return sum;
    }

    /** Absorbs rounding residue so the weights sum to exactly one. */
    private void correctProjectionResidual(Num[] projected, Num residual) {
        for (int index = 0; index < projected.length && !residual.isZero(); index++) {
            Num current = projected[index];
            if (residual.isPositive()) {
                Num adjustment = residual.min(maximumAssetWeight.minus(current));
                projected[index] = current.plus(adjustment);
                residual = residual.minus(adjustment);
            } else {
                Num adjustment = residual.abs().min(current);
                projected[index] = current.minus(adjustment);
                residual = residual.plus(adjustment);
            }
        }
        if (residual.abs().isGreaterThan(series.numFactory().epsilon())) {
            throw new IllegalStateException("failed to project allocation weights");
        }
    }

    private Num clamp(Num value) {
        if (value.isNegative()) {
            return series.numFactory().zero();
        }
        return value.min(maximumAssetWeight);
    }

    private static Num requirePositive(Num value, String asset, int index) {
        if (!Num.isFinite(value) || value.isNegativeOrZero()) {
            throw new IllegalArgumentException(
                    "close price must be finite and > 0 for asset " + asset + " at index " + index);
        }
        return value;
    }
}
