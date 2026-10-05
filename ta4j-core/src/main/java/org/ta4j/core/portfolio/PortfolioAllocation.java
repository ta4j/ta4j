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
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Long-only static target weights for a portfolio backtest.
 *
 * <p>
 * Weights are fractions of total portfolio value ({@code 0.6} means 60%). They
 * may sum to less than {@code 1}; the remainder is the cash target. Sums
 * greater than {@code 1} are rejected to keep execution unlevered. Assets of
 * the {@link PortfolioSeries} that are missing from the allocation have a zero
 * target and are sold on the first rebalance.
 * </p>
 *
 * <p>
 * An allocation is plain target data and does not choose a numeric
 * representation: weights are stored exactly as {@code DecimalNum}, and
 * {@link PortfolioSeriesManager} converts them to the {@link PortfolioSeries}'
 * numeric factory when it runs. {@code Num} weights can be passed as
 * {@code num.bigDecimalValue()} or through {@link #PortfolioAllocation(List)}.
 * </p>
 *
 * <pre>{@code
 * PortfolioAllocation sixtyForty = new PortfolioAllocation(Map.of("SPY", 0.6, "TLT", 0.4));
 * PortfolioAllocation equityAndCash = new PortfolioAllocation(Map.of("SPY", 0.6)); // 40% cash
 * }</pre>
 *
 * @since 0.25.1
 */
public final class PortfolioAllocation {

    private static final NumFactory NUM_FACTORY = DecimalNumFactory.getInstance();

    private final Map<String, Num> targetWeights;
    private final Num totalWeight;
    private final Num zero;
    private final Num one;

    /**
     * Creates an allocation from literal target weights, for example
     * {@code Map.of("SPY", 0.6, "TLT", 0.3)}.
     *
     * <p>
     * Values are converted exactly through their decimal representation; the
     * portfolio run converts them to the portfolio's numeric factory.
     * </p>
     *
     * @param targetWeights target weights keyed by asset name; iteration order is
     *                      retained
     * @since 0.25.1
     */
    public PortfolioAllocation(Map<String, ? extends Number> targetWeights) {
        this(numWeights(targetWeights));
    }

    /**
     * Creates a fully invested allocation by normalizing relative weights so they
     * sum to exactly {@code 1}.
     *
     * @param weightedAssets relative asset weights; repeated assets are summed
     * @since 0.25.1
     */
    public PortfolioAllocation(List<WeightedValue<String>> weightedAssets) {
        this(normalizedTargetWeights(weightedAssets));
    }

    private PortfolioAllocation(LinkedHashMap<String, Num> targetWeights) {
        if (targetWeights.isEmpty()) {
            throw new IllegalArgumentException("targetWeights must not be empty");
        }

        Map<String, Num> normalizedWeights = new LinkedHashMap<>();
        Num normalizedTotalWeight = NUM_FACTORY.zero();
        for (Map.Entry<String, Num> entry : targetWeights.entrySet()) {
            String asset = requireAsset(entry.getKey());
            Num weight = normalizeWeight(asset, entry.getValue());
            normalizedWeights.put(asset, weight);
            normalizedTotalWeight = normalizedTotalWeight.plus(weight);
        }

        Num unitWeight = NUM_FACTORY.one();
        if (normalizedTotalWeight.isGreaterThan(unitWeight.plus(NUM_FACTORY.epsilon()))) {
            throw new IllegalArgumentException("sum of target weights must be <= 1 but was " + normalizedTotalWeight);
        }
        if (normalizedTotalWeight.isGreaterThan(unitWeight)) {
            // Absorb representation noise such as 0.1 + 0.2 + 0.7 in double math.
            for (Map.Entry<String, Num> entry : normalizedWeights.entrySet()) {
                entry.setValue(entry.getValue().dividedBy(normalizedTotalWeight));
            }
            normalizedTotalWeight = unitWeight;
        }

        this.targetWeights = Collections.unmodifiableMap(normalizedWeights);
        this.totalWeight = normalizedTotalWeight;
        this.zero = NUM_FACTORY.zero();
        this.one = unitWeight;
    }

    /**
     * @return target weights in allocation order
     * @since 0.25.1
     */
    public Map<String, Num> getTargetWeights() {
        return targetWeights;
    }

    /**
     * Returns the target weight for an asset, or zero when it is intentionally
     * unallocated.
     *
     * @param asset asset name
     * @return target weight as a fraction of portfolio value
     * @since 0.25.1
     */
    public Num getTargetWeight(String asset) {
        return targetWeights.getOrDefault(requireAsset(asset), zero);
    }

    /**
     * @return sum of all asset target weights
     * @since 0.25.1
     */
    public Num getTotalWeight() {
        return totalWeight;
    }

    /**
     * @return cash target weight ({@code 1 - totalWeight})
     * @since 0.25.1
     */
    public Num getCashWeight() {
        return one.minus(totalWeight);
    }

    /**
     * @return compact description with every target weight and the cash weight
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("PortfolioAllocation{");
        for (Map.Entry<String, Num> entry : targetWeights.entrySet()) {
            text.append(entry.getKey()).append('=').append(entry.getValue()).append(", ");
        }
        return text.append("cash=").append(getCashWeight()).append('}').toString();
    }

    private static LinkedHashMap<String, Num> numWeights(Map<String, ? extends Number> targetWeights) {
        Objects.requireNonNull(targetWeights, "targetWeights");
        LinkedHashMap<String, Num> weights = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends Number> entry : targetWeights.entrySet()) {
            String asset = requireAsset(entry.getKey());
            Number weight = Objects.requireNonNull(entry.getValue(), "weight for " + asset);
            try {
                // Decimal conversion keeps finite values beyond the double range exact.
                weights.put(asset, NUM_FACTORY.numOf(weight));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("target weight must be finite for asset " + asset, exception);
            }
        }
        return weights;
    }

    private static LinkedHashMap<String, Num> normalizedTargetWeights(List<WeightedValue<String>> weightedAssets) {
        Objects.requireNonNull(weightedAssets, "weightedAssets");
        if (weightedAssets.isEmpty()) {
            throw new IllegalArgumentException("weightedAssets must not be empty");
        }

        List<WeightedValue<String>> validatedWeights = new ArrayList<>(weightedAssets.size());
        for (WeightedValue<String> weightedAsset : weightedAssets) {
            Objects.requireNonNull(weightedAsset, "weightedAssets must not contain null entries");
            String asset = requireAsset(weightedAsset.value());
            validatedWeights.add(new WeightedValue<>(asset, normalizeWeight(asset, weightedAsset.weight())));
        }
        LinkedHashMap<String, Num> normalizedWeights = new LinkedHashMap<>();
        for (WeightedValue<String> weightedAsset : WeightedValue.normalizeWeights(validatedWeights, NUM_FACTORY)) {
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

    private static Num normalizeWeight(String asset, Num weight) {
        Objects.requireNonNull(weight, "weight for " + asset);
        if (!Num.isFinite(weight)) {
            throw new IllegalArgumentException("target weight must be finite for asset " + asset);
        }
        if (weight.isNegative()) {
            throw new IllegalArgumentException("target weight must be >= 0 for asset " + asset);
        }
        return NUM_FACTORY.numOf(weight.bigDecimalValue());
    }
}
