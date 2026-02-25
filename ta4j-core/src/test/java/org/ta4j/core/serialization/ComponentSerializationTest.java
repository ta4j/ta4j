/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.gson.JsonParseException;
import java.util.Map;

import org.junit.Test;

public class ComponentSerializationTest {

    @Test
    public void parsePlainTextNameAsLabel() {
        ComponentDescriptor descriptor = ComponentSerialization.parse("Entry");

        assertThat(descriptor.getLabel()).isEqualTo("Entry");
        assertThat(descriptor.getType()).isNull();
        assertThat(descriptor.getComponents()).isEmpty();
    }

    @Test
    public void serializeAndParseRoundTrip() {
        ComponentDescriptor descriptor = ComponentDescriptor.builder()
                .withType("AndRule")
                .addComponent(ComponentDescriptor.labelOnly("Entry"))
                .addComponent(ComponentDescriptor.typeOnly("FixedRule"))
                .build();

        String json = ComponentSerialization.toJson(descriptor);
        ComponentDescriptor parsed = ComponentSerialization.parse(json);

        assertThat(parsed).isEqualTo(descriptor);
    }

    @Test
    public void parseNestedStructure() {
        String json = "{\"type\":\"OrRule\",\"rules\":[{\"label\":\"Entry\"},null,{\"type\":\"NotRule\",\"rules\":[{\"label\":\"Exit\"}]}]}";

        ComponentDescriptor descriptor = ComponentSerialization.parse(json);

        assertThat(descriptor.getType()).isEqualTo("OrRule");
        assertThat(descriptor.getComponents()).hasSize(3);
        assertThat(descriptor.getComponents().get(0).getLabel()).isEqualTo("Entry");
        assertThat(descriptor.getComponents().get(1)).isNull();
        assertThat(descriptor.getComponents().get(2).getType()).isEqualTo("NotRule");
        assertThat(descriptor.getComponents().get(2).getComponents()).hasSize(1);
        assertThat(descriptor.getComponents().get(2).getComponents().get(0).getLabel()).isEqualTo("Exit");
    }

    @Test
    public void parseNullReturnsNull() {
        ComponentDescriptor descriptor = ComponentSerialization.parse(null);

        assertThat(descriptor).isNull();
    }

    @Test
    public void parseEmptyStringReturnsNull() {
        ComponentDescriptor descriptor = ComponentSerialization.parse("");

        assertThat(descriptor).isNull();
    }

    @Test
    public void parseWhitespaceOnlyReturnsNull() {
        ComponentDescriptor descriptor = ComponentSerialization.parse("   ");

        assertThat(descriptor).isNull();
    }

    @Test
    public void parseMalformedJsonSyntaxReturnsLabelOnly() {
        String malformedJson = "{invalid json syntax}";

        ComponentDescriptor descriptor = ComponentSerialization.parse(malformedJson);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.getLabel()).isEqualTo(malformedJson);
        assertThat(descriptor.getType()).isNull();
        assertThat(descriptor.getComponents()).isEmpty();
    }

    @Test
    public void parseUnclosedBraceReturnsLabelOnly() {
        String unclosedJson = "{\"type\":\"Test\"";

        ComponentDescriptor descriptor = ComponentSerialization.parse(unclosedJson);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.getLabel()).isEqualTo(unclosedJson);
        assertThat(descriptor.getType()).isNull();
    }

    @Test
    public void parseInvalidJsonArraySyntaxReturnsLabelOnly() {
        String invalidArray = "{\"rules\":[{\"type\":\"Test\"}";

        ComponentDescriptor descriptor = ComponentSerialization.parse(invalidArray);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.getLabel()).isEqualTo(invalidArray);
        assertThat(descriptor.getType()).isNull();
    }

    @Test
    public void parseNonObjectNonStringPrimitiveThrowsException() {
        assertThrows(JsonParseException.class, () -> {
            ComponentSerialization.parse("123");
        });
    }

    @Test
    public void parseBooleanPrimitiveThrowsException() {
        assertThrows(JsonParseException.class, () -> {
            ComponentSerialization.parse("true");
        });
    }

    @Test
    public void parseNullJsonPrimitiveReturnsNull() {
        ComponentDescriptor descriptor = ComponentSerialization.parse("null");

        assertThat(descriptor).isNull();
    }

    @Test
    public void parseEmptyObjectReturnsEmptyDescriptor() {
        ComponentDescriptor descriptor = ComponentSerialization.parse("{}");

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.getType()).isNull();
        assertThat(descriptor.getLabel()).isNull();
        assertThat(descriptor.getComponents()).isEmpty();
        assertThat(descriptor.getParameters()).isEmpty();
    }

    @Test
    public void parseObjectMissingTypeAndLabelReturnsEmptyDescriptor() {
        String json = "{\"parameters\":{}}";

        ComponentDescriptor descriptor = ComponentSerialization.parse(json);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.getType()).isNull();
        assertThat(descriptor.getLabel()).isNull();
        assertThat(descriptor.getParameters()).isEmpty();
    }

    @Test
    public void parseObjectWithNullTypeAndLabelReturnsEmptyDescriptor() {
        String json = "{\"type\":null,\"label\":null}";

        ComponentDescriptor descriptor = ComponentSerialization.parse(json);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.getType()).isNull();
        assertThat(descriptor.getLabel()).isNull();
    }

    @Test
    public void parseArrayWithUnexpectedTypesThrowsException() {
        String json = "{\"rules\":[123,true]}";

        assertThrows(JsonParseException.class, () -> {
            ComponentSerialization.parse(json);
        });
    }

    @Test
    public void parseArrayWithMixedValidAndInvalidTypesThrowsException() {
        String json = "{\"rules\":[{\"label\":\"Entry\"},123]}";

        assertThrows(JsonParseException.class, () -> {
            ComponentSerialization.parse(json);
        });
    }

    @Test
    public void roundTripWithSpecialCharactersInLabel() {
        ComponentDescriptor original = ComponentDescriptor.builder()
                .withLabel("Entry: \"quoted\" & <special> chars")
                .build();

        String json = ComponentSerialization.toJson(original);
        ComponentDescriptor parsed = ComponentSerialization.parse(json);

        assertThat(parsed.getLabel()).isEqualTo(original.getLabel());
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    public void roundTripWithSpecialCharactersInType() {
        ComponentDescriptor original = ComponentDescriptor.builder().withType("Test-Rule_123").build();

        String json = ComponentSerialization.toJson(original);
        ComponentDescriptor parsed = ComponentSerialization.parse(json);

        assertThat(parsed.getType()).isEqualTo(original.getType());
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    public void roundTripWithEmptyStringInLabel() {
        ComponentDescriptor original = ComponentDescriptor.builder().withLabel("").withType("TestRule").build();

        String json = ComponentSerialization.toJson(original);
        ComponentDescriptor parsed = ComponentSerialization.parse(json);

        assertThat(parsed.getLabel()).isEqualTo(original.getLabel());
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    public void roundTripWithUnicodeCharactersInLabel() {
        ComponentDescriptor original = ComponentDescriptor.builder().withLabel("Entry: 测试 🚀 émoji").build();

        String json = ComponentSerialization.toJson(original);
        ComponentDescriptor parsed = ComponentSerialization.parse(json);

        assertThat(parsed.getLabel()).isEqualTo(original.getLabel());
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    public void parseArrayWithExplicitNullEntriesPreservesNulls() {
        String json = "{\"type\":\"AndRule\",\"components\":[null,{\"label\":\"Entry\"},null]}";

        ComponentDescriptor descriptor = ComponentSerialization.parse(json);

        assertThat(descriptor.getType()).isEqualTo("AndRule");
        assertThat(descriptor.getComponents()).hasSize(3);
        assertThat(descriptor.getComponents().get(0)).isNull();
        assertThat(descriptor.getComponents().get(1).getLabel()).isEqualTo("Entry");
        assertThat(descriptor.getComponents().get(2)).isNull();
    }

    @Test
    public void roundTripWithNullEntriesInComponentArray() {
        ComponentDescriptor original = ComponentDescriptor.builder()
                .withType("OrRule")
                .addComponent(null)
                .addComponent(ComponentDescriptor.labelOnly("Entry"))
                .addComponent(null)
                .build();

        String json = ComponentSerialization.toJson(original);
        ComponentDescriptor parsed = ComponentSerialization.parse(json);

        assertThat(parsed.getComponents()).hasSize(3);
        assertThat(parsed.getComponents().get(0)).isNull();
        assertThat(parsed.getComponents().get(1).getLabel()).isEqualTo("Entry");
        assertThat(parsed.getComponents().get(2)).isNull();
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    public void parseNestedArrayWithNullEntries() {
        String json = "{\"type\":\"AndRule\",\"components\":[{\"type\":\"OrRule\",\"components\":[null,{\"label\":\"Nested\"}]}]}";

        ComponentDescriptor descriptor = ComponentSerialization.parse(json);

        assertThat(descriptor.getType()).isEqualTo("AndRule");
        assertThat(descriptor.getComponents()).hasSize(1);
        ComponentDescriptor nested = descriptor.getComponents().get(0);
        assertThat(nested.getType()).isEqualTo("OrRule");
        assertThat(nested.getComponents()).hasSize(2);
        assertThat(nested.getComponents().get(0)).isNull();
        assertThat(nested.getComponents().get(1).getLabel()).isEqualTo("Nested");
    }

    // --- Regression: component-field routing precedence (rules vs components) ---

    @Test
    public void parseMixedPayloadIndicatorPrefersComponents() {
        // Indicator type with both rules and components: components takes precedence
        String json = "{\"type\":\"SMAIndicator\",\"parameters\":{\"barCount\":3},"
                + "\"rules\":[{\"label\":\"wrong\"}]," + "\"components\":[{\"type\":\"ClosePriceIndicator\"}]}";

        ComponentDescriptor descriptor = ComponentSerialization.parse(json);

        assertThat(descriptor.getType()).isEqualTo("SMAIndicator");
        assertThat(descriptor.getComponents()).hasSize(1);
        assertThat(descriptor.getComponents().get(0).getType()).isEqualTo("ClosePriceIndicator");
    }

    @Test
    public void parseMixedPayloadRulePrefersComponents() {
        // Rule type with both rules and components: components takes precedence
        String json = "{\"type\":\"AndRule\",\"label\":\"entry\"," + "\"rules\":[{\"label\":\"wrong\"}],"
                + "\"components\":[{\"label\":\"Entry\"},{\"type\":\"FixedRule\"}]}";

        ComponentDescriptor descriptor = ComponentSerialization.parse(json);

        assertThat(descriptor.getType()).isEqualTo("AndRule");
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents().get(0).getLabel()).isEqualTo("Entry");
        assertThat(descriptor.getComponents().get(1).getType()).isEqualTo("FixedRule");
    }

    @Test
    public void parseMixedPayloadStrategyPrefersRules() {
        // Strategy type with both rules and components: rules takes precedence
        String json = "{\"type\":\"BaseStrategy\",\"label\":\"Test\",\"parameters\":{\"unstableBars\":1},"
                + "\"components\":[{\"label\":\"wrong\"}],"
                + "\"rules\":[{\"type\":\"SerializableRule\",\"label\":\"entry\",\"parameters\":{\"satisfied\":true}},"
                + "{\"type\":\"SerializableRule\",\"label\":\"exit\",\"parameters\":{\"satisfied\":false}}]}";

        ComponentDescriptor descriptor = ComponentSerialization.parse(json);

        assertThat(descriptor.getType()).isEqualTo("BaseStrategy");
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents().get(0).getLabel()).isEqualTo("entry");
        assertThat(descriptor.getComponents().get(1).getLabel()).isEqualTo("exit");
    }

    @Test
    public void parseLegacyChildrenPayload() {
        String json = "{\"type\":\"OrRule\",\"children\":[{\"label\":\"Entry\"},{\"type\":\"NotRule\",\"children\":[{\"label\":\"Exit\"}]}]}";

        ComponentDescriptor descriptor = ComponentSerialization.parse(json);

        assertThat(descriptor.getType()).isEqualTo("OrRule");
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents().get(0).getLabel()).isEqualTo("Entry");
        assertThat(descriptor.getComponents().get(1).getType()).isEqualTo("NotRule");
        assertThat(descriptor.getComponents().get(1).getComponents()).hasSize(1);
        assertThat(descriptor.getComponents().get(1).getComponents().get(0).getLabel()).isEqualTo("Exit");
    }

    @Test
    public void parseLegacyBaseIndicatorsPayload() {
        String json = "{\"type\":\"SMAIndicator\",\"parameters\":{\"barCount\":2},\"baseIndicators\":[{\"type\":\"ClosePriceIndicator\"}]}";

        ComponentDescriptor descriptor = ComponentSerialization.parse(json);

        assertThat(descriptor.getType()).isEqualTo("SMAIndicator");
        assertThat(descriptor.getComponents()).hasSize(1);
        assertThat(descriptor.getComponents().get(0).getType()).isEqualTo("ClosePriceIndicator");
    }

    @Test
    public void parseLegacyChildrenTakesPrecedenceOverBaseIndicators() {
        // When only legacy fields present, children before baseIndicators (canonical
        // fallback order)
        String json = "{\"type\":\"AndRule\",\"children\":[{\"label\":\"A\"}],\"baseIndicators\":[{\"label\":\"B\"}]}";

        ComponentDescriptor descriptor = ComponentSerialization.parse(json);

        assertThat(descriptor.getType()).isEqualTo("AndRule");
        assertThat(descriptor.getComponents()).hasSize(1);
        assertThat(descriptor.getComponents().get(0).getLabel()).isEqualTo("A");
    }

    @Test
    public void indicatorLabelSuppressionPreserved() {
        // Indicators never serialize labels; verify round-trip strips label from
        // indicator
        ComponentDescriptor indicator = ComponentDescriptor.builder()
                .withType("SMAIndicator")
                .withLabel("ignored")
                .withParameters(Map.of("barCount", 2))
                .addComponent(ComponentDescriptor.typeOnly("ClosePriceIndicator"))
                .build();

        String json = ComponentSerialization.toJson(indicator);

        assertThat(json).doesNotContain("\"label\"");
        ComponentDescriptor parsed = ComponentSerialization.parse(json);
        assertThat(parsed.getType()).isEqualTo("SMAIndicator");
        assertThat(parsed.getLabel()).isNull();
        assertThat(parsed.getComponents()).hasSize(1);
    }
}
