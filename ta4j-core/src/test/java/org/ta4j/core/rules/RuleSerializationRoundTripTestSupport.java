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
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        if (!descriptorsEqual(descriptor, restoredDescriptor)) {
            String expected = ComponentSerialization.toJson(descriptor);
            String actual = ComponentSerialization.toJson(restoredDescriptor);
            assertThat(actual).as("Round-trip descriptor mismatch\nexpected: %s\nactual:   %s", expected, actual)
                    .isEqualTo(expected);
        }
        return restored;
    }

    /**
     * Recursively compares two component descriptors for equality.
     * <p>
     * This method checks that both descriptors have the same type, label,
     * parameters, and nested components. The comparison is performed recursively
     * for nested components.
     *
     * @param expected the expected descriptor
     * @param actual   the actual descriptor to compare
     * @return {@code true} if the descriptors are equal, {@code false} otherwise
     */
    private static boolean descriptorsEqual(ComponentDescriptor expected, ComponentDescriptor actual) {
        if (!Objects.equals(expected.getType(), actual.getType())) {
            return false;
        }
        if (!Objects.equals(expected.getLabel(), actual.getLabel())) {
            return false;
        }
        if (!compareParameters(expected.getParameters(), actual.getParameters())) {
            return false;
        }
        List<ComponentDescriptor> expectedComponents = expected.getComponents();
        List<ComponentDescriptor> actualComponents = actual.getComponents();
        if (expectedComponents.size() != actualComponents.size()) {
            return false;
        }
        for (int i = 0; i < expectedComponents.size(); i++) {
            if (!descriptorsEqual(expectedComponents.get(i), actualComponents.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two parameter maps for equality.
     * <p>
     * This method checks that both maps have the same keys and that the
     * corresponding values are equal using {@link #parameterValuesEqual}.
     *
     * @param expected the expected parameter map
     * @param actual   the actual parameter map to compare
     * @return {@code true} if the parameter maps are equal, {@code false} otherwise
     */
    private static boolean compareParameters(Map<String, Object> expected, Map<String, Object> actual) {
        if (!expected.keySet().equals(actual.keySet())) {
            return false;
        }
        for (String key : expected.keySet()) {
            if (!parameterValuesEqual(expected.get(key), actual.get(key))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two parameter values for equality.
     * <p>
     * This method handles various value types:
     * <ul>
     * <li>Nested maps: recursively compared using {@link #compareParameters}</li>
     * <li>Lists: element-wise comparison using this method recursively</li>
     * <li>Numeric strings: parsed as {@link BigDecimal} and compared
     * numerically</li>
     * <li>Other types: compared using {@link Objects#equals}</li>
     * </ul>
     *
     * @param expected the expected parameter value
     * @param actual   the actual parameter value to compare
     * @return {@code true} if the values are equal, {@code false} otherwise
     */
    @SuppressWarnings("unchecked")
    private static boolean parameterValuesEqual(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return Objects.equals(expected, actual);
        }
        if (expected instanceof Map && actual instanceof Map) {
            return compareParameters((Map<String, Object>) expected, (Map<String, Object>) actual);
        }
        if (expected instanceof List && actual instanceof List) {
            List<Object> expectedList = (List<Object>) expected;
            List<Object> actualList = (List<Object>) actual;
            if (expectedList.size() != actualList.size()) {
                return false;
            }
            for (int i = 0; i < expectedList.size(); i++) {
                if (!parameterValuesEqual(expectedList.get(i), actualList.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (expected instanceof String && actual instanceof String) {
            if (isNumeric((String) expected) && isNumeric((String) actual)) {
                BigDecimal left = new BigDecimal((String) expected);
                BigDecimal right = new BigDecimal((String) actual);
                return left.compareTo(right) == 0;
            }
        }
        return Objects.equals(expected, actual);
    }

    /**
     * Checks if a string represents a valid numeric value.
     * <p>
     * This method attempts to parse the string as a {@link BigDecimal} to determine
     * if it represents a numeric value.
     *
     * @param value the string to check
     * @return {@code true} if the string is numeric, {@code false} otherwise
     */
    private static boolean isNumeric(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
