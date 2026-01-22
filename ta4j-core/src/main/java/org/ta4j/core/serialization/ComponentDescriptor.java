/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import java.io.Serializable;
import java.util.*;

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
     * @return the component type, if provided
     */
    public String getType() {
        return type;
    }

    /**
     * Attempts to resolve the type string to a {@link Class}. The type may be a
     * simple class name (for classes in standard packages) or a fully qualified
     * class name.
     *
     * @return the resolved class, or {@code null} if the type is not set or cannot
     *         be resolved
     */
    public Class<?> getTypeClass() {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            // Try fully qualified name first
            return Class.forName(type);
        } catch (ClassNotFoundException e) {
            // Try simple name in standard packages
            String[] standardPackages = { "org.ta4j.core.rules", "org.ta4j.core.indicators", "org.ta4j.core.strategy" };
            for (String pkg : standardPackages) {
                try {
                    return Class.forName(pkg + "." + type);
                } catch (ClassNotFoundException ignored) {
                    // Continue to next package
                }
            }
            return null;
        }
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
     *         never {@code null}. Entries themselves may be {@code null} when the
     *         caller intentionally inserts placeholders (for example,
     *         org.ta4j.core.rules.AbstractRule composite naming helpers keeps
     *         {@code null} slots so child names preserve their original positions).
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

    @Override
    public String toString() {
        return ComponentSerialization.toJson(this);
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
         * <p>
         * Parameter values must be serializable. Supported types include:
         * <ul>
         * <li>Primitive types and their wrapper classes (Integer, Long, Double,
         * etc.)</li>
         * <li>String</li>
         * <li>Number and its subclasses</li>
         * <li>Boolean</li>
         * <li>Enum types</li>
         * <li>Any type implementing {@link java.io.Serializable}</li>
         * <li>null (allowed)</li>
         * </ul>
         * Non-serializable values may cause serialization failures when the descriptor
         * is converted to JSON or other formats.
         *
         * @param parameters parameter map
         * @return the builder
         * @throws IllegalArgumentException if any parameter value is not null and is
         *                                  not an instance of
         *                                  {@link java.io.Serializable}
         */
        public Builder withParameters(Map<String, Object> parameters) {
            if (parameters == null || parameters.isEmpty()) {
                this.parameters = null;
            } else {
                // Validate all values before copying to maintain builder state consistency
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    Object value = entry.getValue();
                    if (value != null && !(value instanceof Serializable)) {
                        throw new IllegalArgumentException(String.format(
                                "Parameter '%s' has unsupported type '%s'. "
                                        + "Parameter values must be null or implement java.io.Serializable.",
                                entry.getKey(), value.getClass().getName()));
                    }
                }
                this.parameters = new LinkedHashMap<>(parameters);
            }
            return this;
        }

        /**
         * Appends a component descriptor.
         * <p>
         * Passing {@code null} intentionally creates a placeholder entry that is
         * serialized as {@code null} by {@link ComponentSerialization}. This is used by
         * rule name helpers (e.g., org.ta4j.core.rules.AbstractRule composite naming
         * helpers) to retain positional information when a child rule's display name is
         * unavailable.
         *
         * @param component component descriptor (or {@code null} placeholder)
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
