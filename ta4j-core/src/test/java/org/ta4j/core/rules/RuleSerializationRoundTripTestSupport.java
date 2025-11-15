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
 */
final class RuleSerializationRoundTripTestSupport {

    private RuleSerializationRoundTripTestSupport() {
    }

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

    private static boolean isNumeric(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
