/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.math.BigDecimal;
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
     * <p>
     * Weights are converted to the target numeric factory before normalization.
     * Conversion to a BigDecimal-backed factory preserves full precision.
     * Conversion to a double-based factory rounds to double precision (the
     * primitive boundary), and throws when a finite weight cannot be represented
     * at all (overflow to infinity or underflow to zero).
     * </p>
     *
     * @param weightedValues weighted values
     * @param numFactory     target numeric factory
     * @param <T>            value type
     * @return normalized weighted values preserving order
     * @throws IllegalArgumentException if list is empty, total weight is zero, or
     *                                  a weight cannot be represented in the
     *                                  target numeric factory
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
     * Entries with missing or NaN resolved values are skipped. Weights and
     * resolved values are converted to the target numeric factory first:
     * BigDecimal-backed targets preserve full precision, while double-based
     * targets round to double precision (the primitive boundary) and throw when
     * a finite value cannot be represented (overflow to infinity or underflow to
     * zero) instead of silently collapsing it.
     * </p>
     *
     * @param weightedValues weighted values
     * @param valueResolver  resolves value to aggregate for each weighted entry
     * @param numFactory     target numeric factory
     * @param <T>            value type
     * @return weighted sum
     * @throws IllegalArgumentException if a weight or resolved value cannot be
     *                                  represented in the target numeric factory
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
        if (!Num.isFinite(value)) {
            throw new IllegalArgumentException(
                    "value " + value + " is not finite and cannot be converted to the target Num factory");
        }
        if (numFactory.one().getDelegate() instanceof BigDecimal) {
            // BigDecimal-backed target: convert via BigDecimal so mantissa digits and
            // magnitude are preserved; never round-trip through a primitive double.
            return numFactory.numOf(value.bigDecimalValue());
        }
        // Double-based target: conversion to the primitive double is the unavoidable
        // precision boundary. Refuse values the double cannot represent at all
        // instead of silently collapsing them.
        double converted = value.doubleValue();
        if (Double.isInfinite(converted)) {
            throw new IllegalArgumentException("value " + value
                    + " overflows the double range of the target Num factory; convert via a BigDecimal-backed factory");
        }
        if (converted == 0.0d && !value.isZero()) {
            throw new IllegalArgumentException("value " + value
                    + " underflows to zero in the target double-based Num factory; convert via a BigDecimal-backed factory");
        }
        return numFactory.numOf(converted);
    }
}
