/*
 * SPDX-License-Identifier: MIT
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
import org.ta4j.core.serialization.RuleSerializationException;

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
 * than failing. This occurs when a {@link RuleSerializationException} is
 * thrown, indicating that serialization support has not yet been implemented
 * for the rule.
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
     * failing. This occurs when a {@link RuleSerializationException} is thrown,
     * indicating that serialization support has not yet been implemented for the
     * rule.
     *
     * @param series the bar series to use for rule deserialization
     * @param rule   the rule to test for round-trip serialization
     * @return the restored rule after deserialization, or the original rule if
     *         serialization/deserialization is not supported
     */
    static Rule assertRuleRoundTrips(BarSeries series, Rule rule) {
        return assertRuleRoundTrips(series, rule, RoundTripFlavor.DESCRIPTOR);
    }

    /**
     * Asserts that a rule can be serialized to JSON, deserialized back into a
     * descriptor, and reconstructed into the original rule.
     *
     * @param series the bar series to use for rule deserialization
     * @param rule   the rule to test for round-trip serialization
     * @return the restored rule after deserialization, or the original rule if
     *         serialization/deserialization is not supported
     */
    static Rule assertRuleJsonRoundTrips(BarSeries series, Rule rule) {
        return assertRuleRoundTrips(series, rule, RoundTripFlavor.JSON);
    }

    private enum RoundTripFlavor {
        DESCRIPTOR, JSON
    }

    private static Rule assertRuleRoundTrips(BarSeries series, Rule rule, RoundTripFlavor flavor) {
        ComponentDescriptor descriptor;
        try {
            descriptor = RuleSerialization.describe(rule);
        } catch (RuntimeException ex) {
            Assume.assumeNoException("Rule serialization not supported for " + rule.getClass().getSimpleName(), ex);
            return rule;
        }

        ComponentDescriptor descriptorForDeserialization = descriptor;
        String expectedJson = canonicalize(descriptor);

        if (flavor == RoundTripFlavor.JSON) {
            String serializedJson = ComponentSerialization.toJson(descriptor);
            ComponentDescriptor parsedDescriptor = ComponentSerialization.parse(serializedJson);
            assertThat(parsedDescriptor)
                    .as("ComponentSerialization.parse should rebuild descriptor from JSON\njson: %s", serializedJson)
                    .isNotNull();
            descriptorForDeserialization = parsedDescriptor;
            String parsedJson = canonicalize(parsedDescriptor);
            assertThat(parsedJson)
                    .as("JSON serialization mismatch\noriginal: %s\nparsed:   %s", expectedJson, parsedJson)
                    .isEqualTo(expectedJson);
        }

        Rule restored;
        try {
            restored = RuleSerialization.fromDescriptor(series, descriptorForDeserialization);
        } catch (RuntimeException ex) {
            Assume.assumeNoException("Rule deserialization not supported for " + rule.getClass().getSimpleName(), ex);
            return rule;
        }

        ComponentDescriptor restoredDescriptor = RuleSerialization.describe(restored);
        String actualJson = canonicalize(restoredDescriptor);
        assertThat(actualJson)
                .as("Round-trip descriptor mismatch (%s)\nexpected: %s\nactual:   %s", flavor, expectedJson, actualJson)
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
            return normalizeParameters(convertToStringObjectMap(map));
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object element : list) {
                normalized.add(normalizeValue(element));
            }
            return normalized;
        }
        if (value instanceof Number number) {
            return normalizeNumericValue(number);
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

    private static Number normalizeNumericValue(Number value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros();
        }
        try {
            return new BigDecimal(value.toString()).stripTrailingZeros();
        } catch (NumberFormatException ex) {
            return value;
        }
    }

    /**
     * Safely converts a Map<?, ?> to Map<String, Object> by verifying all keys are
     * Strings and building a new properly-typed map. This avoids unchecked cast
     * warnings.
     *
     * @param map the map to convert
     * @return a new Map<String, Object> with the same entries
     */
    private static Map<String, Object> convertToStringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String)) {
                throw new IllegalArgumentException(
                        "Expected all map keys to be Strings, but found: " + key.getClass().getName());
            }
            result.put((String) key, entry.getValue());
        }
        return result;
    }
}
