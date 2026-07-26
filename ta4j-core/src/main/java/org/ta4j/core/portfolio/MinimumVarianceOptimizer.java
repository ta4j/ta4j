/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.ArrayList;
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
 * The optimizer estimates a population covariance matrix from aligned one-bar
 * simple returns and minimizes portfolio variance on the bounded probability
 * simplex. It does not estimate expected returns. The optional maximum asset
 * weight provides a concentration constraint without changing the long-only,
 * fully invested contract.
 * </p>
 *
 * <p>
 * Calculations remain in the portfolio {@link NumFactory}. A deterministic
 * projected-gradient solver avoids matrix inversion, so singular covariance
 * matrices are supported.
 * </p>
 *
 * @since 0.23.1
 */
public final class MinimumVarianceOptimizer {

    private static final int MAX_ITERATIONS = 20_000;
    private static final int PROJECTION_ITERATIONS = 128;

    private final PortfolioSeries series;
    private final int index;
    private final int barCount;
    private final Num maximumAssetWeight;

    /**
     * Creates an uncapped optimizer over all available simple returns.
     *
     * @param series aligned portfolio series
     * @since 0.23.1
     */
    public MinimumVarianceOptimizer(PortfolioSeries series) {
        this(series, Objects.requireNonNull(series, "series").getEndIndex(), series.getEndIndex(),
                series.numFactory().one());
    }

    /**
     * Creates a capped optimizer over all available simple returns.
     *
     * @param series             aligned portfolio series
     * @param maximumAssetWeight maximum weight for any asset
     * @since 0.23.1
     */
    public MinimumVarianceOptimizer(PortfolioSeries series, Num maximumAssetWeight) {
        this(series, Objects.requireNonNull(series, "series").getEndIndex(), series.getEndIndex(), maximumAssetWeight);
    }

    /**
     * Creates an uncapped optimizer over an explicit historical return window.
     *
     * @param series   aligned portfolio series
     * @param index    final aligned index included in estimation
     * @param barCount number of one-bar return observations
     * @since 0.23.1
     */
    public MinimumVarianceOptimizer(PortfolioSeries series, int index, int barCount) {
        this(series, index, barCount, Objects.requireNonNull(series, "series").numFactory().one());
    }

    /**
     * Creates a capped optimizer over an explicit historical return window.
     *
     * @param series             aligned portfolio series
     * @param index              final aligned index included in estimation
     * @param barCount           number of one-bar return observations
     * @param maximumAssetWeight maximum weight for any asset
     * @since 0.23.1
     */
    public MinimumVarianceOptimizer(PortfolioSeries series, int index, int barCount, Num maximumAssetWeight) {
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
    }

    /**
     * Computes the minimum-variance allocation.
     *
     * @return fully invested long-only allocation
     * @throws IllegalArgumentException if the estimation window contains invalid
     *                                  prices or returns
     * @throws IllegalStateException    if the numerical solver does not converge
     * @since 0.23.1
     */
    public PortfolioAllocation optimize() {
        Num[][] covariance = covarianceMatrix();
        List<Num> weights = optimize(covariance);
        Map<String, Num> targetWeights = new LinkedHashMap<>();
        for (int assetIndex = 0; assetIndex < series.getAssets().size(); assetIndex++) {
            targetWeights.put(series.getAssets().get(assetIndex), weights.get(assetIndex));
        }
        return new PortfolioAllocation(targetWeights, series.numFactory());
    }

    private Num normalizeMaximumWeight(Num maximumWeight) {
        Objects.requireNonNull(maximumWeight, "maximumAssetWeight");
        Num normalized = series.toPortfolioNum(maximumWeight);
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

    private Num[][] covarianceMatrix() {
        int assetCount = series.getAssets().size();
        NumFactory numFactory = series.numFactory();
        Num[][] returns = new Num[barCount][assetCount];
        int firstReturnIndex = index - barCount + 1;

        for (int observation = 0; observation < barCount; observation++) {
            int currentIndex = firstReturnIndex + observation;
            for (int assetIndex = 0; assetIndex < assetCount; assetIndex++) {
                String asset = series.getAssets().get(assetIndex);
                Num previous = requirePositive(series.getClosePrice(asset, currentIndex - 1), asset, currentIndex - 1);
                Num current = requirePositive(series.getClosePrice(asset, currentIndex), asset, currentIndex);
                Num value = current.dividedBy(previous).minus(numFactory.one());
                if (!Num.isFinite(value)) {
                    throw new IllegalArgumentException(
                            "simple return must be finite for asset " + asset + " at index " + currentIndex);
                }
                returns[observation][assetIndex] = value;
            }
        }

        Num observationCount = numFactory.numOf(barCount);
        Num[] means = new Num[assetCount];
        for (int assetIndex = 0; assetIndex < assetCount; assetIndex++) {
            Num sum = numFactory.zero();
            for (Num[] observation : returns) {
                sum = sum.plus(observation[assetIndex]);
            }
            means[assetIndex] = sum.dividedBy(observationCount);
        }

        Num[][] covariance = new Num[assetCount][assetCount];
        for (int row = 0; row < assetCount; row++) {
            for (int column = row; column < assetCount; column++) {
                Num sum = numFactory.zero();
                for (Num[] observation : returns) {
                    Num rowDifference = observation[row].minus(means[row]);
                    Num columnDifference = observation[column].minus(means[column]);
                    sum = sum.plus(rowDifference.multipliedBy(columnDifference));
                }
                Num value = sum.dividedBy(observationCount);
                if (!Num.isFinite(value)) {
                    throw new IllegalArgumentException("covariance matrix must contain only finite values");
                }
                covariance[row][column] = value;
                covariance[column][row] = value;
            }
        }
        return covariance;
    }

    private List<Num> optimize(Num[][] covariance) {
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
        List<Num> weights = equalWeights(assetCount);
        if (lipschitzBound.isZero()) {
            return weights;
        }

        Num step = one.dividedBy(lipschitzBound);
        List<Num> accelerated = weights;
        Num acceleration = one;

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            List<Num> gradient = gradient(covariance, accelerated, two);
            List<Num> unprojected = new ArrayList<>(assetCount);
            for (int assetIndex = 0; assetIndex < assetCount; assetIndex++) {
                unprojected.add(accelerated.get(assetIndex).minus(step.multipliedBy(gradient.get(assetIndex))));
            }
            List<Num> nextWeights = project(unprojected);
            if (maximumDifference(weights, nextWeights).isLessThanOrEqual(tolerance)) {
                return nextWeights;
            }

            Num nextAcceleration = one.plus(one.plus(four.multipliedBy(acceleration.multipliedBy(acceleration))).sqrt())
                    .dividedBy(two);
            Num momentum = acceleration.minus(one).dividedBy(nextAcceleration);
            List<Num> nextAccelerated = new ArrayList<>(assetCount);
            for (int assetIndex = 0; assetIndex < assetCount; assetIndex++) {
                Num delta = nextWeights.get(assetIndex).minus(weights.get(assetIndex));
                nextAccelerated.add(nextWeights.get(assetIndex).plus(momentum.multipliedBy(delta)));
            }

            weights = nextWeights;
            accelerated = nextAccelerated;
            acceleration = nextAcceleration;
        }

        throw new IllegalStateException("minimum-variance optimization did not converge");
    }

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

    private List<Num> gradient(Num[][] covariance, List<Num> weights, Num two) {
        List<Num> gradient = new ArrayList<>(covariance.length);
        for (Num[] row : covariance) {
            Num value = series.numFactory().zero();
            for (int column = 0; column < row.length; column++) {
                value = value.plus(row[column].multipliedBy(weights.get(column)));
            }
            gradient.add(two.multipliedBy(value));
        }
        return gradient;
    }

    private List<Num> project(List<Num> values) {
        NumFactory numFactory = series.numFactory();
        for (Num value : values) {
            if (!Num.isFinite(value)) {
                throw new IllegalStateException("minimum-variance optimization did not converge");
            }
        }
        Num lower = values.getFirst().minus(maximumAssetWeight);
        Num upper = values.getFirst();
        for (Num value : values) {
            lower = lower.min(value.minus(maximumAssetWeight));
            upper = upper.max(value);
        }

        for (int iteration = 0; iteration < PROJECTION_ITERATIONS; iteration++) {
            Num threshold = lower.plus(upper).dividedBy(numFactory.two());
            Num sum = numFactory.zero();
            for (Num value : values) {
                sum = sum.plus(clamp(value.minus(threshold)));
            }
            if (sum.isGreaterThan(numFactory.one())) {
                lower = threshold;
            } else {
                upper = threshold;
            }
        }

        Num threshold = lower.plus(upper).dividedBy(numFactory.two());
        List<Num> projected = new ArrayList<>(values.size());
        Num sum = numFactory.zero();
        for (Num value : values) {
            Num projectedValue = clamp(value.minus(threshold));
            projected.add(projectedValue);
            sum = sum.plus(projectedValue);
        }
        correctProjectionResidual(projected, numFactory.one().minus(sum));
        return List.copyOf(projected);
    }

    private void correctProjectionResidual(List<Num> projected, Num residual) {
        for (int index = 0; index < projected.size() && !residual.isZero(); index++) {
            Num current = projected.get(index);
            if (residual.isPositive()) {
                Num adjustment = residual.min(maximumAssetWeight.minus(current));
                projected.set(index, current.plus(adjustment));
                residual = residual.minus(adjustment);
            } else {
                Num adjustment = residual.abs().min(current);
                projected.set(index, current.minus(adjustment));
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

    private List<Num> equalWeights(int assetCount) {
        Num weight = series.numFactory().one().dividedBy(series.numFactory().numOf(assetCount));
        List<Num> weights = new ArrayList<>(assetCount);
        for (int index = 0; index < assetCount; index++) {
            weights.add(weight);
        }
        return List.copyOf(weights);
    }

    private Num maximumDifference(List<Num> first, List<Num> second) {
        Num maximum = series.numFactory().zero();
        for (int index = 0; index < first.size(); index++) {
            maximum = maximum.max(first.get(index).minus(second.get(index)).abs());
        }
        return maximum;
    }

    private static Num requirePositive(Num value, String asset, int index) {
        if (!Num.isFinite(value) || value.isNegativeOrZero()) {
            throw new IllegalArgumentException(
                    "close price must be finite and > 0 for asset " + asset + " at index " + index);
        }
        return value;
    }
}
