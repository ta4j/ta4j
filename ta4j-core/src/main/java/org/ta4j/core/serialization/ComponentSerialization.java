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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * Utility class for serializing and deserializing {@link ComponentDescriptor}
 * instances with Gson.
 *
 * @since 0.19
 */
public final class ComponentSerialization {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ComponentDescriptor.class, new ComponentDescriptorAdapter())
            .create();

    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private ComponentSerialization() {
        // utility class
    }

    /**
     * Serializes the descriptor into a JSON document.
     *
     * @param descriptor component descriptor
     * @return JSON payload
     */
    public static String toJson(ComponentDescriptor descriptor) {
        return GSON.toJson(descriptor);
    }

    /**
     * Parses JSON into a descriptor. Plain text inputs are interpreted as label
     * values.
     *
     * @param json JSON payload
     * @return descriptor instance or {@code null}
     */
    public static ComponentDescriptor parse(String json) {
        if (json == null) {
            return null;
        }
        String trimmed = json.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return GSON.fromJson(trimmed, ComponentDescriptor.class);
        } catch (JsonSyntaxException ex) {
            return ComponentDescriptor.labelOnly(json);
        }
    }

    private static final class ComponentDescriptorAdapter
            implements JsonSerializer<ComponentDescriptor>, JsonDeserializer<ComponentDescriptor> {

        private static final String FIELD_TYPE = "type";
        private static final String FIELD_LABEL = "label";
        private static final String FIELD_PARAMETERS = "parameters";
        private static final String FIELD_RULES = "rules";
        private static final String FIELD_CHILDREN = "children";

        @Override
        public JsonElement serialize(ComponentDescriptor src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) {
                return JsonNull.INSTANCE;
            }
            JsonObject object = new JsonObject();
            if (src.getType() != null) {
                object.addProperty(FIELD_TYPE, src.getType());
            }
            if (src.getLabel() != null) {
                object.addProperty(FIELD_LABEL, src.getLabel());
            }
            if (!src.getParameters().isEmpty()) {
                object.add(FIELD_PARAMETERS, context.serialize(src.getParameters(), MAP_TYPE));
            }
            if (!src.getChildren().isEmpty()) {
                JsonArray array = new JsonArray();
                for (ComponentDescriptor child : src.getChildren()) {
                    if (child == null) {
                        array.add(JsonNull.INSTANCE);
                    } else {
                        array.add(serialize(child, typeOfSrc, context));
                    }
                }
                object.add(FIELD_RULES, array);
            }
            return object;
        }

        @Override
        public ComponentDescriptor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                return ComponentDescriptor.labelOnly(json.getAsString());
            }
            if (!json.isJsonObject()) {
                throw new JsonParseException("Unsupported component descriptor payload: " + json);
            }
            JsonObject object = json.getAsJsonObject();
            ComponentDescriptor.Builder builder = ComponentDescriptor.builder();
            if (object.has(FIELD_TYPE) && !object.get(FIELD_TYPE).isJsonNull()) {
                builder.withType(object.get(FIELD_TYPE).getAsString());
            }
            if (object.has(FIELD_LABEL) && !object.get(FIELD_LABEL).isJsonNull()) {
                builder.withLabel(object.get(FIELD_LABEL).getAsString());
            }
            JsonElement paramsElement = object.get(FIELD_PARAMETERS);
            if (paramsElement != null && !paramsElement.isJsonNull()) {
                Map<String, Object> parameters = context.deserialize(paramsElement, MAP_TYPE);
                builder.withParameters(parameters);
            }
            JsonElement rulesElement = resolveRulesElement(object);
            if (rulesElement != null && rulesElement.isJsonArray()) {
                JsonArray array = rulesElement.getAsJsonArray();
                List<ComponentDescriptor> children = new ArrayList<>(array.size());
                for (JsonElement element : array) {
                    ComponentDescriptor child = deserialize(element, typeOfT, context);
                    children.add(child);
                }
                for (ComponentDescriptor child : children) {
                    builder.addChild(child);
                }
            }
            return builder.build();
        }

        private JsonElement resolveRulesElement(JsonObject object) {
            if (object.has(FIELD_RULES)) {
                return object.get(FIELD_RULES);
            }
            if (object.has(FIELD_CHILDREN)) {
                return object.get(FIELD_CHILDREN);
            }
            return null;
        }
    }
}
