/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.ArrayList;
import java.util.Collections;
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
 * Explicit weights may sum to less than {@code 1}; the remainder is held as
 * cash. Weights greater than {@code 1} are rejected to keep portfolio execution
 * unlevered and deterministic.
 * </p>
 *
 * @since 0.23.1
 */
public final class PortfolioAllocation {

    private final Map<String, Num> targetWeights;
    private final Num totalWeight;
    private final Num zero;
    private final Num one;

    /**
     * Creates an allocation from explicit portfolio-fraction target weights.
     *
     * @param targetWeights target weights keyed by asset name
     * @param numFactory    numeric factory for portfolio accounting
     * @since 0.23.1
     */
    public PortfolioAllocation(Map<String, Num> targetWeights, NumFactory numFactory) {
        Objects.requireNonNull(targetWeights, "targetWeights");
        Objects.requireNonNull(numFactory, "numFactory");
        if (targetWeights.isEmpty()) {
            throw new IllegalArgumentException("targetWeights must not be empty");
        }

        Map<String, Num> normalizedWeights = new LinkedHashMap<>();
        Num normalizedTotalWeight = numFactory.zero();
        for (Map.Entry<String, Num> entry : targetWeights.entrySet()) {
            String asset = requireAsset(entry.getKey());
            Num weight = normalizeWeight(entry.getValue(), numFactory);
            if (weight.isNegative()) {
                throw new IllegalArgumentException("target weight must be >= 0 for asset " + asset);
            }
            normalizedWeights.put(asset, weight);
            normalizedTotalWeight = normalizedTotalWeight.plus(weight);
        }

        Num unitWeight = numFactory.one();
        if (normalizedTotalWeight.isGreaterThan(unitWeight.plus(numFactory.epsilon()))) {
            throw new IllegalArgumentException("sum of target weights must be <= 1");
        }
        if (normalizedTotalWeight.isGreaterThan(unitWeight)) {
            for (Map.Entry<String, Num> entry : normalizedWeights.entrySet()) {
                entry.setValue(entry.getValue().dividedBy(normalizedTotalWeight));
            }
            normalizedTotalWeight = unitWeight;
        }

        this.targetWeights = Collections.unmodifiableMap(normalizedWeights);
        this.totalWeight = normalizedTotalWeight;
        this.zero = numFactory.zero();
        this.one = unitWeight;
    }

    /**
     * Creates a fully invested allocation by normalizing relative weighted asset
     * values.
     *
     * @param weightedAssets relative asset weights
     * @param numFactory     numeric factory for portfolio accounting
     * @since 0.23.1
     */
    public PortfolioAllocation(List<WeightedValue<String>> weightedAssets, NumFactory numFactory) {
        this(normalizedTargetWeights(weightedAssets, numFactory), numFactory);
    }

    /**
     * @return target weights in deterministic asset order
     * @since 0.23.1
     */
    public Map<String, Num> getTargetWeights() {
        return targetWeights;
    }

    /**
     * Returns the target weight for an asset or zero when it is intentionally
     * unallocated.
     *
     * @param asset asset name
     * @return target weight
     * @since 0.23.1
     */
    public Num getTargetWeight(String asset) {
        return targetWeights.getOrDefault(requireAsset(asset), zero);
    }

    /**
     * @return sum of all asset target weights
     * @since 0.23.1
     */
    public Num getTotalWeight() {
        return totalWeight;
    }

    /**
     * @return cash target weight
     * @since 0.23.1
     */
    public Num getCashWeight() {
        return one.minus(totalWeight);
    }

    private static Map<String, Num> normalizedTargetWeights(List<WeightedValue<String>> weightedAssets,
            NumFactory numFactory) {
        Objects.requireNonNull(weightedAssets, "weightedAssets");
        Objects.requireNonNull(numFactory, "numFactory");
        if (weightedAssets.isEmpty()) {
            throw new IllegalArgumentException("weightedAssets must not be empty");
        }

        List<WeightedValue<String>> validatedWeights = new ArrayList<>(weightedAssets.size());
        for (WeightedValue<String> weightedAsset : weightedAssets) {
            Objects.requireNonNull(weightedAsset, "weightedAssets must not contain null entries");
            String asset = requireAsset(weightedAsset.value());
            Num weight = normalizeWeight(weightedAsset.weight(), numFactory);
            if (weight.isNegative()) {
                throw new IllegalArgumentException("weight must be >= 0 for asset " + asset);
            }
            validatedWeights.add(new WeightedValue<>(asset, weight));
        }
        Map<String, Num> normalizedWeights = new LinkedHashMap<>();
        for (WeightedValue<String> weightedAsset : WeightedValue.normalizeWeights(validatedWeights, numFactory)) {
            normalizedWeights.merge(weightedAsset.value(), weightedAsset.weight(), Num::plus);
        }
        return normalizedWeights;
    }

    private static String requireAsset(String asset) {
        Objects.requireNonNull(asset, "asset");
        if (asset.isBlank()) {
            throw new IllegalArgumentException("asset must not be blank");
        }
        return asset;
    }

    private static Num normalizeWeight(Num weight, NumFactory numFactory) {
        Objects.requireNonNull(weight, "weight");
        if (!Num.isFinite(weight)) {
            throw new IllegalArgumentException("target weight must be finite");
        }
        return numFactory.numOf(weight.bigDecimalValue());
    }
}
