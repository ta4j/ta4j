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
package org.ta4j.core.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class ComponentSerializationTest {

    @Test
    public void parsePlainTextNameAsLabel() {
        ComponentDescriptor descriptor = ComponentSerialization.parse("Entry");

        assertThat(descriptor.getLabel()).isEqualTo("Entry");
        assertThat(descriptor.getType()).isNull();
        assertThat(descriptor.getChildren()).isEmpty();
    }

    @Test
    public void serializeAndParseRoundTrip() {
        ComponentDescriptor descriptor = ComponentDescriptor.builder()
                .withType("AndRule")
                .addChild(ComponentDescriptor.labelOnly("Entry"))
                .addChild(ComponentDescriptor.typeOnly("FixedRule"))
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
        assertThat(descriptor.getChildren()).hasSize(3);
        assertThat(descriptor.getChildren().get(0).getLabel()).isEqualTo("Entry");
        assertThat(descriptor.getChildren().get(1)).isNull();
        assertThat(descriptor.getChildren().get(2).getType()).isEqualTo("NotRule");
        assertThat(descriptor.getChildren().get(2).getChildren()).hasSize(1);
        assertThat(descriptor.getChildren().get(2).getChildren().get(0).getLabel()).isEqualTo("Exit");
    }
}
