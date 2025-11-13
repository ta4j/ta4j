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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Describes a TA4J component (rule, indicator, strategy, etc.) for structured
 * JSON serialization.
 *
 * @since 0.19
 */
public final class ComponentDescriptor {

    private final String type;
    private final String label;
    private final Map<String, Object> parameters;
    private final List<ComponentDescriptor> components;

    private ComponentDescriptor(Builder builder) {
        this.type = builder.type;
        this.label = builder.label;
        if (builder.parameters == null || builder.parameters.isEmpty()) {
            this.parameters = Collections.emptyMap();
        } else {
            this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.parameters));
        }
        if (builder.components == null || builder.components.isEmpty()) {
            this.components = Collections.emptyList();
        } else {
            this.components = Collections.unmodifiableList(new ArrayList<>(builder.components));
        }
    }

    /**
     * @return the component type, if provided
     */
    public String getType() {
        return type;
    }

    /**
     * @return a display label for the component, if provided
     */
    public String getLabel() {
        return label;
    }

    /**
     * @return component parameters, never {@code null}
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * @return component descriptors (indicators/rules used by this component),
     *         never {@code null}
     */
    public List<ComponentDescriptor> getComponents() {
        return components;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComponentDescriptor)) {
            return false;
        }
        ComponentDescriptor that = (ComponentDescriptor) o;
        return Objects.equals(type, that.type) && Objects.equals(label, that.label)
                && Objects.equals(parameters, that.parameters) && Objects.equals(components, that.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, label, parameters, components);
    }

    /**
     * Creates a descriptor with just the type populated.
     *
     * @param type component type
     * @return descriptor instance
     */
    public static ComponentDescriptor typeOnly(String type) {
        return builder().withType(type).build();
    }

    /**
     * Creates a descriptor with only a label populated.
     *
     * @param label component label
     * @return descriptor instance
     */
    public static ComponentDescriptor labelOnly(String label) {
        return builder().withLabel(label).build();
    }

    /**
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ComponentDescriptor} instances.
     */
    public static final class Builder {

        private String type;
        private String label;
        private Map<String, Object> parameters;
        private List<ComponentDescriptor> components;

        private Builder() {
        }

        /**
         * Sets the component type.
         *
         * @param type component type
         * @return the builder
         */
        public Builder withType(String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the component label.
         *
         * @param label component label
         * @return the builder
         */
        public Builder withLabel(String label) {
            this.label = label;
            return this;
        }

        /**
         * Adds or replaces component parameters.
         *
         * @param parameters parameter map
         * @return the builder
         */
        public Builder withParameters(Map<String, Object> parameters) {
            if (parameters == null || parameters.isEmpty()) {
                this.parameters = null;
            } else {
                this.parameters = new LinkedHashMap<>(parameters);
            }
            return this;
        }

        /**
         * Appends a component descriptor.
         *
         * @param component component descriptor, may be {@code null}
         * @return the builder
         */
        public Builder addComponent(ComponentDescriptor component) {
            if (components == null) {
                components = new ArrayList<>();
            }
            components.add(component);
            return this;
        }

        /**
         * Builds the descriptor.
         *
         * @return descriptor instance
         */
        public ComponentDescriptor build() {
            return new ComponentDescriptor(this);
        }
    }
}
