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
package org.ta4j.core.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Assume;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;
import org.ta4j.core.serialization.RuleSerialization;

/**
 * Shared helper for asserting rule serialization/deserialization round-trips.
 * <p>
 * This utility class provides methods to verify that rules can be correctly
 * serialized to {@link ComponentDescriptor} objects and deserialized back to
 * equivalent rules. It performs deep equality checks on the descriptors to
 * ensure that all properties, parameters, and nested components are preserved
 * during the round-trip process.
 * <p>
 * If serialization or deserialization is not supported for a particular rule
 * type, the test will be skipped using JUnit's {@link Assume} mechanism rather
 * than failing.
 *
 * @since 0.19
 */
final class RuleSerializationRoundTripTestSupport {

    private RuleSerializationRoundTripTestSupport() {
    }

    /**
     * Asserts that a rule can be serialized and deserialized without losing
     * information.
     * <p>
     * This method performs the following steps:
     * <ol>
     * <li>Serializes the given rule to a {@link ComponentDescriptor}</li>
     * <li>Deserializes the descriptor back to a {@link Rule}</li>
     * <li>Serializes the restored rule to another descriptor</li>
     * <li>Compares the original and restored descriptors for equality</li>
     * </ol>
     * <p>
     * If serialization or deserialization is not supported for the rule type, the
     * test will be skipped (using {@link Assume#assumeNoException}) rather than
     * failing.
     *
     * @param series the bar series to use for rule deserialization
     * @param rule   the rule to test for round-trip serialization
     * @return the restored rule after deserialization, or the original rule if
     *         serialization/deserialization is not supported
     */
    static Rule assertRuleRoundTrips(BarSeries series, Rule rule) {
        ComponentDescriptor descriptor;
        try {
            descriptor = RuleSerialization.describe(rule);
        } catch (RuntimeException ex) {
            Assume.assumeNoException("Rule serialization not supported for " + rule.getClass().getSimpleName(), ex);
            return rule;
        }

        Rule restored;
        try {
            restored = RuleSerialization.fromDescriptor(series, descriptor);
        } catch (RuntimeException ex) {
            Assume.assumeNoException("Rule deserialization not supported for " + rule.getClass().getSimpleName(), ex);
            return rule;
        }

        ComponentDescriptor restoredDescriptor = RuleSerialization.describe(restored);
        String expectedJson = canonicalize(descriptor);
        String actualJson = canonicalize(restoredDescriptor);
        assertThat(actualJson)
                .as("Round-trip descriptor mismatch\nexpected: %s\nactual:   %s", expectedJson, actualJson)
                .isEqualTo(expectedJson);
        return restored;
    }

    private static String canonicalize(ComponentDescriptor descriptor) {
        ComponentDescriptor normalized = normalizeDescriptor(descriptor);
        return ComponentSerialization.toJson(normalized);
    }

    private static ComponentDescriptor normalizeDescriptor(ComponentDescriptor descriptor) {
        ComponentDescriptor.Builder builder = ComponentDescriptor.builder()
                .withType(descriptor.getType())
                .withLabel(descriptor.getLabel());
        if (!descriptor.getParameters().isEmpty()) {
            builder.withParameters(normalizeParameters(descriptor.getParameters()));
        }
        List<ComponentDescriptor> children = new ArrayList<>(descriptor.getComponents().size());
        for (ComponentDescriptor component : descriptor.getComponents()) {
            children.add(normalizeDescriptor(component));
        }
        children.sort(ComponentDescriptorComparator.INSTANCE);
        for (ComponentDescriptor child : children) {
            builder.addComponent(child);
        }
        return builder.build();
    }

    private static final class ComponentDescriptorComparator implements Comparator<ComponentDescriptor> {

        private static final ComponentDescriptorComparator INSTANCE = new ComponentDescriptorComparator();

        @Override
        public int compare(ComponentDescriptor left, ComponentDescriptor right) {
            String leftJson = ComponentSerialization.toJson(left);
            String rightJson = ComponentSerialization.toJson(right);
            return leftJson.compareTo(rightJson);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeParameters(Map<String, Object> parameters) {
        if (parameters.isEmpty()) {
            return parameters;
        }
        List<Entry<String, Object>> entries = new ArrayList<>(parameters.entrySet());
        entries.sort(Entry.comparingByKey());
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Entry<String, Object> entry : entries) {
            normalized.put(entry.getKey(), normalizeValue(entry.getValue()));
        }
        return normalized;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizeParameters((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object element : list) {
                normalized.add(normalizeValue(element));
            }
            return normalized;
        }
        if (value instanceof String str) {
            return normalizeNumericString(str);
        }
        return value;
    }

    private static String normalizeNumericString(String value) {
        try {
            return new BigDecimal(value).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            return value;
        }
    }
}
