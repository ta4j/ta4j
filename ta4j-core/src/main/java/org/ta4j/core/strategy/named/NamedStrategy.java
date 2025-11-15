/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.strategy.named;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.serialization.ComponentDescriptor;

/**
 * Base class for strategies that can be reconstructed from compact name tokens.
 *
 * @since 0.19
 */
public abstract class NamedStrategy extends BaseStrategy {

    /**
     * JSON {@code type} written by {@link #toDescriptor()}.
     */
    public static final String SERIALIZED_TYPE = NamedStrategy.class.getSimpleName();

    private static final Map<String, Class<? extends NamedStrategy>> REGISTRY = new ConcurrentHashMap<>();

    /**
     * Protected constructor that allows subclasses to provide the fully formatted
     * label (and therefore {@link Strategy#getName()}).
     *
     * @param label        strategy label that also serves as the serialized value
     * @param entryRule    entry rule
     * @param exitRule     exit rule
     * @param unstableBars unstable bars
     */
    protected NamedStrategy(String label, Rule entryRule, Rule exitRule, int unstableBars) {
        super(label, entryRule, exitRule, unstableBars);
        registerImplementation(getClass());
    }

    /**
     * Protected constructor that defaults {@code unstableBars} to {@code 0}.
     *
     * @param label     strategy label that also serves as the serialized value
     * @param entryRule entry rule
     * @param exitRule  exit rule
     */
    protected NamedStrategy(String label, Rule entryRule, Rule exitRule) {
        super(label, entryRule, exitRule);
        registerImplementation(getClass());
    }

    /**
     * Registers a {@link NamedStrategy} implementation so it can be reconstructed
     * purely from its compact label. Custom strategies should invoke this method
     * during application startup (typically from a static initializer).
     *
     * @param type strategy subtype
     */
    public static void registerImplementation(Class<? extends NamedStrategy> type) {
        Objects.requireNonNull(type, "type");
        String key = type.getSimpleName();
        REGISTRY.compute(key, (name, existing) -> {
            if (existing != null && existing != type) {
                throw new IllegalStateException(
                        "Named strategy already registered for simple name " + name + ": " + existing.getName());
            }
            return type;
        });
    }

    /**
     * Resolves a previously registered named strategy type.
     *
     * @param simpleName simple class name (without package)
     * @return optional containing the registered type
     */
    public static Optional<Class<? extends NamedStrategy>> lookup(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(REGISTRY.get(simpleName));
    }

    /**
     * Builds the serialized label using the simple class name and optional
     * parameters.
     *
     * @param type       concrete strategy type
     * @param parameters constructor parameters encoded as strings
     * @return compact strategy label
     */
    public static String buildLabel(Class<? extends NamedStrategy> type, String... parameters) {
        Objects.requireNonNull(type, "type");
        if (parameters == null || parameters.length == 0) {
            return type.getSimpleName();
        }
        return type.getSimpleName() + '_' + String.join("_", parameters);
    }

    /**
     * Splits a serialized label into the simple class name and parameter tokens.
     *
     * @param label serialized label
     * @return immutable token list where index {@code 0} is the simple class name
     */
    public static List<String> splitLabel(String label) {
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("Named strategy label cannot be blank");
        }
        return Collections.unmodifiableList(Arrays.asList(label.split("_", -1)));
    }

    /**
     * Builds strategies for every provided parameter permutation.
     *
     * @param series                backing bar series
     * @param parameterPermutations ordered permutations of constructor parameters
     * @param factory               factory responsible for instantiating the
     *                              strategy
     * @param <T>                   concrete named strategy type
     * @return list of instantiated strategies
     */
    public static <T extends NamedStrategy> List<Strategy> buildAllStrategyPermutations(BarSeries series,
            Iterable<String[]> parameterPermutations, Factory<T> factory) {
        return buildAllStrategyPermutations(series, parameterPermutations, factory, null);
    }

    /**
     * Builds strategies for every provided parameter permutation.
     *
     * @param series                backing bar series
     * @param parameterPermutations ordered permutations of constructor parameters
     * @param factory               factory responsible for instantiating the
     *                              strategy
     * @param failureHandler        optional handler that receives the parameter
     *                              snapshot alongside the
     *                              {@link IllegalArgumentException} thrown by the
     *                              factory. When {@code null} the exception is
     *                              rethrown.
     * @param <T>                   concrete named strategy type
     * @return list of instantiated strategies
     */
    public static <T extends NamedStrategy> List<Strategy> buildAllStrategyPermutations(BarSeries series,
            Iterable<String[]> parameterPermutations, Factory<T> factory,
            BiConsumer<String[], IllegalArgumentException> failureHandler) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(parameterPermutations, "parameterPermutations");
        Objects.requireNonNull(factory, "factory");

        List<Strategy> strategies = new ArrayList<>();
        for (String[] parameters : parameterPermutations) {
            if (parameters == null) {
                throw new IllegalArgumentException("Parameter entry cannot be null");
            }
            String[] args = Arrays.copyOf(parameters, parameters.length);
            try {
                strategies.add(factory.create(series, args));
            } catch (IllegalArgumentException ex) {
                if (failureHandler == null) {
                    throw ex;
                }
                failureHandler.accept(Arrays.copyOf(args, args.length), ex);
            }
        }
        return strategies;
    }

    /**
     * Factory interface used by
     * {@link #buildAllStrategyPermutations(BarSeries, Iterable, Factory)}.
     *
     * @param <T> concrete named strategy type
     */
    @FunctionalInterface
    public interface Factory<T extends NamedStrategy> {
        T create(BarSeries series, String... parameters);
    }

    /**
     * Helper used by the serialization layer to enforce that a strategy has been
     * registered.
     *
     * @param simpleName named strategy simple class name
     * @return registered type
     */
    public static Class<? extends NamedStrategy> requireRegistered(String simpleName) {
        return lookup(simpleName).orElseThrow(() -> new IllegalArgumentException("Unknown named strategy '" + simpleName
                + "'. Ensure it is registered via NamedStrategy.registerImplementation()."));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        return ComponentDescriptor.builder().withType(SERIALIZED_TYPE).withLabel(getName()).build();
    }

    @Override
    public String toString() {
        return getName();
    }
}
