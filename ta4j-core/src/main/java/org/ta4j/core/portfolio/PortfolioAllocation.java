/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.analysis.WeightedValue;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Long-only static target weights for a portfolio backtest.
 *
 * <p>
 * Weights are explicit and may sum to less than {@code 1}; the remainder is
 * held as cash. Weights greater than {@code 1} are rejected to keep the first
 * portfolio slice unlevered and deterministic.
 * </p>
 *
 * @since 0.22.9
 */
public final class PortfolioAllocation {

    private final Map<PortfolioAsset, Num> targetWeights;
    private final Num totalWeight;
    private final Num zero;
    private final Num one;

    private PortfolioAllocation(Map<PortfolioAsset, Num> targetWeights, Num totalWeight, NumFactory numFactory) {
        this.targetWeights = Map.copyOf(targetWeights);
        this.totalWeight = totalWeight;
        this.zero = numFactory.zero();
        this.one = numFactory.one();
    }

    /**
     * Creates explicit static target weights.
     *
     * @param targetWeights asset target weights
     * @param numFactory    numeric factory for portfolio accounting
     * @return portfolio allocation
     * @since 0.22.9
     */
    public static PortfolioAllocation targetWeights(Map<PortfolioAsset, Num> targetWeights, NumFactory numFactory) {
        Objects.requireNonNull(targetWeights, "targetWeights");
        Objects.requireNonNull(numFactory, "numFactory");
        if (targetWeights.isEmpty()) {
            throw new IllegalArgumentException("targetWeights must not be empty");
        }

        Map<PortfolioAsset, Num> normalizedWeights = new LinkedHashMap<>();
        Num totalWeight = numFactory.zero();
        for (Map.Entry<PortfolioAsset, Num> entry : targetWeights.entrySet()) {
            PortfolioAsset asset = Objects.requireNonNull(entry.getKey(), "targetWeights must not contain null assets");
            Num weight = normalizeWeight(entry.getValue(), numFactory);
            if (weight.isNegative()) {
                throw new IllegalArgumentException("target weight must be >= 0 for asset " + asset);
            }
            normalizedWeights.put(asset, weight);
            totalWeight = totalWeight.plus(weight);
        }

        if (totalWeight.isGreaterThan(numFactory.one())) {
            throw new IllegalArgumentException("sum of target weights must be <= 1");
        }
        return new PortfolioAllocation(normalizedWeights, totalWeight, numFactory);
    }

    /**
     * Creates a fully invested allocation by normalizing weighted asset inputs.
     *
     * <p>
     * This factory reuses ta4j's shared {@link WeightedValue} normalization
     * primitive and is useful when callers have relative weights that should be
     * scaled to exactly {@code 1}.
     * </p>
     *
     * @param weightedAssets weighted assets
     * @param numFactory     numeric factory for portfolio accounting
     * @return fully invested portfolio allocation
     * @since 0.22.9
     */
    public static PortfolioAllocation fullyInvested(List<WeightedValue<PortfolioAsset>> weightedAssets,
            NumFactory numFactory) {
        List<WeightedValue<PortfolioAsset>> normalizedAssets = WeightedValue.normalizeWeights(weightedAssets,
                numFactory);
        Map<PortfolioAsset, Num> targetWeights = new LinkedHashMap<>();
        for (WeightedValue<PortfolioAsset> weightedAsset : normalizedAssets) {
            targetWeights.merge(weightedAsset.value(), weightedAsset.weight(), Num::plus);
        }
        return targetWeights(targetWeights, numFactory);
    }

    /**
     * @return target weights by asset
     * @since 0.22.9
     */
    public Map<PortfolioAsset, Num> targetWeights() {
        return targetWeights;
    }

    /**
     * Returns the target weight for an asset or zero when it is intentionally
     * unallocated.
     *
     * @param asset asset id
     * @return target weight
     * @since 0.22.9
     */
    public Num targetWeight(PortfolioAsset asset) {
        Objects.requireNonNull(asset, "asset");
        return targetWeights.getOrDefault(asset, zero);
    }

    /**
     * @return sum of all asset target weights
     * @since 0.22.9
     */
    public Num totalWeight() {
        return totalWeight;
    }

    /**
     * @return cash target weight
     * @since 0.22.9
     */
    public Num cashWeight() {
        return one.minus(totalWeight);
    }

    private static Num normalizeWeight(Num weight, NumFactory numFactory) {
        Objects.requireNonNull(weight, "weight");
        if (Num.isNaNOrNull(weight) || Double.isInfinite(weight.doubleValue())) {
            throw new IllegalArgumentException("target weight must be finite");
        }
        if (numFactory.produces(weight)) {
            return weight;
        }
        return numFactory.numOf(weight.doubleValue());
    }
}
