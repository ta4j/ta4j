/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.RuleSerialization;

final class RuleCopies {

    private RuleCopies() {
    }

    static Rule copy(Rule rule) {
        Rule source = Objects.requireNonNull(rule, "rule");
        try {
            ComponentDescriptor descriptor = RuleSerialization.describe(source);
            BarSeries series = findBarSeries(source).orElseGet(RuleCopies::emptySeries);
            return RuleSerialization.fromDescriptor(series, descriptor);
        } catch (RuntimeException ex) {
            return source;
        }
    }

    private static BarSeries emptySeries() {
        return new BaseBarSeriesBuilder().build();
    }

    private static Optional<BarSeries> findBarSeries(Object value) {
        return findBarSeries(value, new IdentityHashMap<>());
    }

    private static Optional<BarSeries> findBarSeries(Object value, IdentityHashMap<Object, Boolean> visited) {
        if (value == null || visited.containsKey(value)) {
            return Optional.empty();
        }
        visited.put(value, Boolean.TRUE);

        if (value instanceof BarSeries series) {
            return Optional.of(series);
        }
        if (value instanceof Indicator<?> indicator) {
            BarSeries series = indicator.getBarSeries();
            return series == null ? Optional.empty() : Optional.of(series);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                Optional<BarSeries> series = findBarSeries(element, visited);
                if (series.isPresent()) {
                    return series;
                }
            }
            return Optional.empty();
        }
        if (value instanceof Map<?, ?> map) {
            Optional<BarSeries> keySeries = findBarSeries(map.keySet(), visited);
            if (keySeries.isPresent()) {
                return keySeries;
            }
            return findBarSeries(map.values(), visited);
        }

        Class<?> valueType = value.getClass();
        if (valueType.isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Optional<BarSeries> series = findBarSeries(Array.get(value, i), visited);
                if (series.isPresent()) {
                    return series;
                }
            }
            return Optional.empty();
        }

        return findBarSeriesInFields(value, valueType, visited);
    }

    private static Optional<BarSeries> findBarSeriesInFields(Object value, Class<?> valueType,
            IdentityHashMap<Object, Boolean> visited) {
        Class<?> currentType = valueType;
        while (currentType != null && currentType != Object.class) {
            Package currentPackage = currentType.getPackage();
            if (currentPackage != null && currentPackage.getName().startsWith("java.")) {
                return Optional.empty();
            }
            for (Field field : currentType.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Optional<BarSeries> series = findBarSeriesInField(value, field, visited);
                if (series.isPresent()) {
                    return series;
                }
            }
            currentType = currentType.getSuperclass();
        }
        return Optional.empty();
    }

    private static Optional<BarSeries> findBarSeriesInField(Object value, Field field,
            IdentityHashMap<Object, Boolean> visited) {
        try {
            field.setAccessible(true);
            return findBarSeries(field.get(value), visited);
        } catch (IllegalAccessException | RuntimeException ex) {
            return Optional.empty();
        }
    }
}
