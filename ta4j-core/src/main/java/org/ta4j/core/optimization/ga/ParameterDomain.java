/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.optimization.ga;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import org.apache.commons.math3.random.RandomGenerator;

/**
 * Declares one explicit gene domain for a parameterized search space.
 *
 * <p>
 * Domains are finite and deterministic. Numeric, enum-like, and constrained
 * boolean genes are all modeled as ordered allowed values.
 *
 * @param <T> decoded value type
 * @since 0.22.7
 */
public final class ParameterDomain<T> {

    private final String name;
    private final List<T> values;

    private ParameterDomain(String name, List<T> values) {
        this.name = requireName(name);
        this.values = List.copyOf(requireValues(values));
    }

    /**
     * Creates a domain from an explicit ordered value list.
     *
     * @param name   stable parameter name
     * @param values allowed values in deterministic order
     * @param <T>    decoded value type
     * @return immutable parameter domain
     * @since 0.22.7
     */
    public static <T> ParameterDomain<T> ofValues(String name, List<T> values) {
        return new ParameterDomain<>(name, values);
    }

    /**
     * Creates an integer range domain using an inclusive upper bound.
     *
     * @param name           stable parameter name
     * @param startInclusive first allowed integer
     * @param endInclusive   last allowed integer
     * @param step           positive step size
     * @return integer parameter domain
     * @since 0.22.7
     */
    public static ParameterDomain<Integer> integerRange(String name, int startInclusive, int endInclusive, int step) {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be > 0");
        }
        if (endInclusive < startInclusive) {
            throw new IllegalArgumentException("endInclusive must be >= startInclusive");
        }
        List<Integer> values = new ArrayList<>();
        for (int value = startInclusive; value <= endInclusive; value += step) {
            values.add(value);
        }
        return ofValues(name, values);
    }

    /**
     * Creates a constrained boolean domain.
     *
     * @param name          stable parameter name
     * @param allowedValues allowed boolean values, for example {@code true} only or
     *                      {@code false, true}
     * @return constrained boolean parameter domain
     * @since 0.22.7
     */
    public static ParameterDomain<Boolean> constrainedBoolean(String name, boolean... allowedValues) {
        Objects.requireNonNull(allowedValues, "allowedValues");
        if (allowedValues.length == 0) {
            throw new IllegalArgumentException("allowedValues must not be empty");
        }
        List<Boolean> values = new ArrayList<>(allowedValues.length);
        for (boolean value : allowedValues) {
            values.add(value);
        }
        return ofValues(name, values);
    }

    /**
     * @return stable parameter name
     * @since 0.22.7
     */
    public String name() {
        return name;
    }

    /**
     * @return immutable allowed values in deterministic order
     * @since 0.22.7
     */
    public List<T> values() {
        return values;
    }

    /**
     * @return allowed value count
     * @since 0.22.7
     */
    public int size() {
        return values.size();
    }

    /**
     * Decodes one allele index into its value.
     *
     * @param alleleIndex zero-based index inside this domain
     * @return decoded value
     * @since 0.22.7
     */
    public T decode(int alleleIndex) {
        if (alleleIndex < 0 || alleleIndex >= values.size()) {
            throw new IllegalArgumentException("alleleIndex out of range for domain '" + name + "': " + alleleIndex);
        }
        return values.get(alleleIndex);
    }

    int randomIndex(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        return random.nextInt(values.size());
    }

    int mutateIndex(int currentIndex, RandomGenerator random) {
        decode(currentIndex);
        Objects.requireNonNull(random, "random");
        if (values.size() == 1) {
            return currentIndex;
        }
        int candidate = currentIndex;
        while (candidate == currentIndex) {
            candidate = randomIndex(random);
        }
        return candidate;
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name;
    }

    private static <T> List<T> requireValues(List<T> values) {
        Objects.requireNonNull(values, "values");
        LinkedHashSet<T> deduplicated = new LinkedHashSet<>();
        for (T value : values) {
            if (value == null) {
                throw new IllegalArgumentException("values must not contain null");
            }
            deduplicated.add(value);
        }
        if (deduplicated.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        return new ArrayList<>(deduplicated);
    }
}
