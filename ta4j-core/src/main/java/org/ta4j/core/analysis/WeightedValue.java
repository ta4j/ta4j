/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Value associated with a finite numeric weight.
 *
 * <p>
 * This reusable primitive centralizes common weighting operations used across
 * ranking, objective scoring, and confidence aggregation.
 * </p>
 *
 * @param <T>    value type
 * @param value  weighted value
 * @param weight finite weight
 * @since 0.22.4
 */
public record WeightedValue<T>(T value, Num weight) {

    /**
     * Creates a validated weighted value.
     *
     * @param value  weighted value
     * @param weight finite weight
     * @since 0.22.4
     */
    public WeightedValue {
        Objects.requireNonNull(value, "value");
        validateWeight(weight);
    }

    /**
     * Normalizes weights so their sum is exactly {@code 1}.
     *
     * @param weightedValues weighted values
     * @param numFactory     target numeric factory
     * @param <T>            value type
     * @return normalized weighted values preserving order
     * @throws IllegalArgumentException if list is empty or total weight is zero
     * @since 0.22.4
     */
    public static <T> List<WeightedValue<T>> normalizeWeights(List<WeightedValue<T>> weightedValues,
            NumFactory numFactory) {
        Objects.requireNonNull(weightedValues, "weightedValues");
        Objects.requireNonNull(numFactory, "numFactory");
        if (weightedValues.isEmpty()) {
            throw new IllegalArgumentException("weightedValues must not be empty");
        }

        List<WeightedValue<T>> normalizedInput = new ArrayList<>(weightedValues.size());
        Num totalWeight = numFactory.zero();
        for (WeightedValue<T> weightedValue : weightedValues) {
            Objects.requireNonNull(weightedValue, "weightedValues must not contain null entries");
            Num normalizedWeight = normalize(weightedValue.weight(), numFactory);
            validateWeight(normalizedWeight);
            normalizedInput.add(new WeightedValue<>(weightedValue.value(), normalizedWeight));
            totalWeight = totalWeight.plus(normalizedWeight);
        }
        if (totalWeight.isZero()) {
            throw new IllegalArgumentException("sum of weights must be > 0");
        }

        List<WeightedValue<T>> normalizedValues = new ArrayList<>(normalizedInput.size());
        for (WeightedValue<T> weightedValue : normalizedInput) {
            Num normalizedWeight = weightedValue.weight().dividedBy(totalWeight);
            normalizedValues.add(new WeightedValue<>(weightedValue.value(), normalizedWeight));
        }
        return List.copyOf(normalizedValues);
    }

    /**
     * Computes weighted sum for resolved values.
     *
     * <p>
     * Entries with missing or NaN resolved values are skipped.
     * </p>
     *
     * @param weightedValues weighted values
     * @param valueResolver  resolves value to aggregate for each weighted entry
     * @param numFactory     target numeric factory
     * @param <T>            value type
     * @return weighted sum
     * @since 0.22.4
     */
    public static <T> Num weightedSum(List<WeightedValue<T>> weightedValues, Function<T, Num> valueResolver,
            NumFactory numFactory) {
        Objects.requireNonNull(weightedValues, "weightedValues");
        Objects.requireNonNull(valueResolver, "valueResolver");
        Objects.requireNonNull(numFactory, "numFactory");

        Num sum = numFactory.zero();
        for (WeightedValue<T> weightedValue : weightedValues) {
            Objects.requireNonNull(weightedValue, "weightedValues must not contain null entries");
            Num normalizedWeight = normalize(weightedValue.weight(), numFactory);
            validateWeight(normalizedWeight);
            Num resolvedValue = normalize(valueResolver.apply(weightedValue.value()), numFactory);
            if (Num.isNaNOrNull(resolvedValue)) {
                continue;
            }
            sum = sum.plus(normalizedWeight.multipliedBy(resolvedValue));
        }
        return sum;
    }

    private static void validateWeight(Num weight) {
        Objects.requireNonNull(weight, "weight");
        if (Num.isNaNOrNull(weight) || Double.isNaN(weight.doubleValue()) || Double.isInfinite(weight.doubleValue())) {
            throw new IllegalArgumentException("weight must be finite");
        }
    }

    private static Num normalize(Num value, NumFactory numFactory) {
        if (Num.isNaNOrNull(value)) {
            return NaN.NaN;
        }
        if (numFactory.produces(value)) {
            return value;
        }
        return numFactory.numOf(value.doubleValue());
    }
}
