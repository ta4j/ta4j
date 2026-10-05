/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;
import org.ta4j.core.serialization.IndicatorSerialization;

public final class IndicatorSerializationRoundTripTestSupport {

    private IndicatorSerializationRoundTripTestSupport() {
    }

    public static BarSeries serializationSeries(NumFactory numFactory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 160; i++) {
            double base = 100 + i * 0.5 + Math.sin(i / 3.0) * 2.0;
            double open = base + Math.cos(i / 5.0);
            double close = base + Math.sin(i / 7.0);
            double high = Math.max(open, close) + 1.5 + (i % 4) * 0.1;
            double low = Math.min(open, close) - 1.5 - (i % 3) * 0.1;
            double volume = 1_000 + i * 17 + (i % 5) * 23;
            series.barBuilder().openPrice(open).closePrice(close).highPrice(high).lowPrice(low).volume(volume).add();
        }
        return series;
    }

    public static int[] stableIndexes(BarSeries series) {
        int endIndex = series.getEndIndex();
        return new int[] { endIndex - 3, endIndex - 1, endIndex };
    }

    public static <T> void assertIndicatorRoundTrips(BarSeries series, Indicator<T> indicator, int... indexes) {
        assertIndicatorRoundTrips(
                new AbstractIndicatorTest.IndicatorSerializationFixture<>(series, indicator, indexes));
    }

    static void assertIndicatorRoundTrips(AbstractIndicatorTest.IndicatorSerializationFixture<?> fixture) {
        ComponentDescriptor descriptor = fixture.indicator().toDescriptor();
        String expectedJson = canonicalize(descriptor);

        Indicator<?> descriptorRestored = IndicatorSerialization.fromDescriptor(fixture.series(), descriptor);
        assertRestoredIndicator(fixture, descriptorRestored, RoundTripFlavor.DESCRIPTOR, expectedJson);

        String json = fixture.indicator().toJson();
        ComponentDescriptor parsedDescriptor = ComponentSerialization.parse(json);
        assertThat(canonicalize(parsedDescriptor))
                .as("JSON serialization mismatch\noriginal: %s\nparsed:   %s", expectedJson, json)
                .isEqualTo(expectedJson);

        Indicator<?> jsonRestored = Indicator.fromJson(fixture.series(), json);
        assertRestoredIndicator(fixture, jsonRestored, RoundTripFlavor.JSON, expectedJson);
    }

    private enum RoundTripFlavor {
        DESCRIPTOR, JSON
    }

    private static void assertRestoredIndicator(AbstractIndicatorTest.IndicatorSerializationFixture<?> fixture,
            Indicator<?> restored, RoundTripFlavor flavor, String expectedJson) {
        assertThat(restored).as("Restored indicator type (%s)", flavor).isInstanceOf(fixture.indicator().getClass());

        ComponentDescriptor restoredDescriptor = restored.toDescriptor();
        String actualJson = canonicalize(restoredDescriptor);
        assertThat(actualJson)
                .as("Round-trip descriptor mismatch (%s)\nexpected: %s\nactual:   %s", flavor, expectedJson, actualJson)
                .isEqualTo(expectedJson);

        for (int index : fixture.indexes()) {
            Object expected = fixture.indicator().getValue(index);
            Object actual = restored.getValue(index);
            assertValuesEqual(index, expected, actual);
        }
    }

    private static void assertValuesEqual(int index, Object expected, Object actual) {
        if (expected instanceof Num expectedNum) {
            assertThat(actual).as("Value at index %s should be a Num", index).isInstanceOf(Num.class);
            Num actualNum = (Num) actual;
            if (expectedNum.isNaN()) {
                assertThat(actualNum.isNaN()).as("NaN value mismatch at index %s", index).isTrue();
            } else {
                assertThat(actualNum).as("Value mismatch at index %s", index).isEqualByComparingTo(expectedNum);
            }
            return;
        }
        assertThat(actual).as("Value mismatch at index %s", index).isEqualTo(expected);
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
        for (ComponentDescriptor component : descriptor.getComponents()) {
            builder.addComponent(normalizeDescriptor(component));
        }
        return builder.build();
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
