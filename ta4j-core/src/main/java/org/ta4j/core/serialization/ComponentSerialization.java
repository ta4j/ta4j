/*
 * SPDX-License-Identifier: MIT
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
        private static final String FIELD_COMPONENTS = "components";

        /**
         * Determines the field name for components based on the component type.
         * <p>
         * Uses strong type checking first: attempts to resolve the type to a
         * {@link Class} and checks if it's assignable from {@link Strategy},
         * {@link Indicator}, or {@link Rule} interfaces. If class resolution fails,
         * falls back to naming convention checks using {@code endsWith} (the library's
         * naming convention):
         * <ul>
         * <li>Types ending with "Strategy" → "rules"</li>
         * <li>Types ending with "Indicator" or "Rule" → "components"</li>
         * </ul>
         * Defaults to "components" if type is unknown or cannot be determined.
         *
         * @param descriptor component descriptor
         * @return field name for components ("rules" for strategies, "components" for
         *         indicators/rules)
         */
        private static String getComponentsFieldName(ComponentDescriptor descriptor) {
            // Try strong type checking first
            Class<?> typeClass = descriptor.getTypeClass();
            if (typeClass != null) {
                if (org.ta4j.core.Strategy.class.isAssignableFrom(typeClass)) {
                    return FIELD_RULES;
                } else if (org.ta4j.core.Indicator.class.isAssignableFrom(typeClass)
                        || org.ta4j.core.Rule.class.isAssignableFrom(typeClass)) {
                    return FIELD_COMPONENTS;
                }
            }

            // Fall back to naming convention (endsWith per library convention)
            String type = descriptor.getType();
            if (type != null) {
                if (type.endsWith("Strategy")) {
                    return FIELD_RULES;
                } else if (type.endsWith("Indicator") || type.endsWith("Rule")) {
                    return FIELD_COMPONENTS;
                }
            }
            // Default to "components" if type is unknown
            return FIELD_COMPONENTS;
        }

        /**
         * Determines if a descriptor represents an indicator (for label serialization).
         * Indicators should not serialize labels, even when nested in rules.
         * <p>
         * Uses strong type checking first: attempts to resolve the type to a
         * {@link Class} and checks if it's assignable from {@link Indicator} but not
         * {@link Rule}. If class resolution fails, falls back to naming convention
         * checks using {@code endsWith} (the library's naming convention):
         * <ul>
         * <li>Types ending with "Rule" → not an indicator</li>
         * <li>Types ending with "Indicator" → is an indicator</li>
         * </ul>
         *
         * @param descriptor component descriptor
         * @return {@code true} if the descriptor represents an indicator
         */
        private static boolean isIndicator(ComponentDescriptor descriptor) {
            // Try strong type checking first
            Class<?> typeClass = descriptor.getTypeClass();
            if (typeClass != null) {
                // If it's a Rule, it's not an indicator
                if (org.ta4j.core.Rule.class.isAssignableFrom(typeClass)) {
                    return false;
                }
                // If it's an Indicator, it is an indicator
                return org.ta4j.core.Indicator.class.isAssignableFrom(typeClass);
            }

            // Fall back to naming convention (endsWith per library convention)
            String type = descriptor.getType();
            if (type == null) {
                return false;
            }
            // Rules end with "Rule", so if it ends with "Rule", it's not an indicator
            if (type.endsWith("Rule")) {
                return false;
            }
            // If it ends with "Indicator", it's an indicator
            return type.endsWith("Indicator");
        }

        @Override
        public JsonElement serialize(ComponentDescriptor src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) {
                return JsonNull.INSTANCE;
            }
            JsonObject object = new JsonObject();
            if (src.getType() != null) {
                object.addProperty(FIELD_TYPE, src.getType());
            }
            // Serialize label right after type (for consistent JSON ordering)
            // Only for rules/strategies, not for indicators.
            // Indicators (even with labels for matching) don't serialize labels in JSON.
            // Matching during deserialization uses position-based matching for indicators.
            if (src.getLabel() != null && !isIndicator(src)) {
                object.addProperty(FIELD_LABEL, src.getLabel());
            }
            if (!src.getParameters().isEmpty()) {
                object.add(FIELD_PARAMETERS, context.serialize(src.getParameters(), MAP_TYPE));
            }
            if (!src.getComponents().isEmpty()) {
                JsonArray array = new JsonArray();
                for (ComponentDescriptor component : src.getComponents()) {
                    if (component == null) {
                        array.add(JsonNull.INSTANCE);
                    } else {
                        array.add(serialize(component, typeOfSrc, context));
                    }
                }
                // Determine field name based on component type (Indicator/Rule → "components",
                // Strategy → "rules")
                String componentsFieldName = getComponentsFieldName(src);
                object.add(componentsFieldName, array);
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
                List<ComponentDescriptor> components = new ArrayList<>(array.size());
                for (JsonElement element : array) {
                    ComponentDescriptor component = deserialize(element, typeOfT, context);
                    components.add(component);
                }
                for (ComponentDescriptor component : components) {
                    builder.addComponent(component);
                }
            }
            return builder.build();
        }

        private JsonElement resolveRulesElement(JsonObject object) {
            // Check for rules (strategies), then components (indicators/rules), then legacy
            // field names
            if (object.has(FIELD_RULES)) {
                return object.get(FIELD_RULES);
            }
            if (object.has(FIELD_COMPONENTS)) {
                return object.get(FIELD_COMPONENTS);
            }
            // Legacy support for "children" and "baseIndicators"
            if (object.has("children")) {
                return object.get("children");
            }
            if (object.has("baseIndicators")) {
                return object.get("baseIndicators");
            }
            return null;
        }
    }
}
