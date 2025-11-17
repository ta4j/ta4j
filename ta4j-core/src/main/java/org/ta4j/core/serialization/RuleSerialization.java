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

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.helpers.CrossIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.helper.ChainLink;

import java.lang.reflect.*;
import java.util.*;

/**
 * Serializes and deserializes {@link Rule} instances into structured
 * {@link ComponentDescriptor} payloads.
 *
 * <p>
 * The implementation performs a best-effort reflection based introspection to
 * capture the constructor arguments that were used to build a rule. Only rule
 * classes that keep references to their constructor arguments (directly or via
 * nested helper classes) can be reconstructed. Rules that eagerly transform
 * their inputs into derived indicators without keeping the original constructor
 * arguments may not be fully supported.
 *
 * @since 0.19
 */
public final class RuleSerialization {

    private static final String CORE_PACKAGE = "org.ta4j.core";
    private static final String RULE_PACKAGE = "org.ta4j.core.rules";
    private static final String INDICATOR_PACKAGE = "org.ta4j.core.indicators";
    private static final String NUM_PACKAGE = "org.ta4j.core.num";
    private static final String JAVA_LANG_PACKAGE = "java.lang";

    private RuleSerialization() {
    }

    /**
     * Simplifies class names for common types to reduce JSON size. Rules,
     * Indicators, and common java.lang types use simple names.
     *
     * @param clazz the class to simplify
     * @return simplified class name or fully qualified name if not a common type
     */
    private static String simplifyClassName(Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray() && clazz.getComponentType().isPrimitive()) {
            return clazz.getName();
        }
        String packageName = clazz.getPackageName();
        if (packageName == null) {
            return clazz.getName();
        }
        if (packageName.equals(CORE_PACKAGE) || packageName.equals(RULE_PACKAGE)
                || packageName.equals(INDICATOR_PACKAGE) || packageName.equals(NUM_PACKAGE)
                || packageName.equals(JAVA_LANG_PACKAGE)) {
            return clazz.getSimpleName();
        }
        return clazz.getName();
    }

    /**
     * Converts a {@link Rule} into a {@link ComponentDescriptor} hierarchy.
     *
     * @param rule rule instance
     * @return descriptor describing the rule
     */
    public static ComponentDescriptor describe(Rule rule) {
        Objects.requireNonNull(rule, "rule");
        return describe(rule, new IdentityHashMap<>());
    }

    private static ComponentDescriptor describe(Rule rule, IdentityHashMap<Rule, ComponentDescriptor> visited) {
        ComponentDescriptor cached = visited.get(rule);
        if (cached != null) {
            return cached;
        }

        ConstructorMatch match = ConstructorMatch.locate(rule);
        if (match == null) {
            throw new IllegalArgumentException("Unable to describe rule " + rule.getClass().getName()
                    + ": no supported constructor signature found");
        }

        Class<?> ruleClass = rule.getClass();
        String typeName = ruleClass.getPackageName().equals("org.ta4j.core.rules") ? ruleClass.getSimpleName()
                : ruleClass.getName();
        ComponentDescriptor.Builder builder = ComponentDescriptor.builder().withType(typeName);

        Map<String, Object> parameters = new LinkedHashMap<>();
        List<ComponentDescriptor> components = new ArrayList<>();

        ArgumentContext context = new ArgumentContext(parameters, components, visited);
        for (Argument argument : match.arguments) {
            argument.serialize(context);
        }

        // Serialize components and parameters; deserialization infers constructor
        // signature from these
        if (!parameters.isEmpty()) {
            builder.withParameters(parameters);
        }
        for (ComponentDescriptor component : components) {
            builder.addComponent(component);
        }

        ComponentDescriptor descriptor = builder.build();

        // Check if the rule has a custom name set via setName()
        // The rule's getName() returns either:
        // 1. A custom name (set via setName()) - which could be any string
        // 2. The default name - which is a JSON representation (either type-only or
        // with components)
        String currentName = rule.getName();

        // Try to determine if currentName is the default name
        // Default names are JSON strings that start with "{"
        boolean hasCustomName = true;
        if (currentName != null && currentName.trim().startsWith("{")) {
            // Current name looks like JSON - try to parse it
            try {
                ComponentDescriptor currentDescriptor = ComponentSerialization.parse(currentName);
                if (currentDescriptor != null) {
                    // Successfully parsed - check if it matches our descriptor structure
                    // For default names, the type should match
                    String currentType = currentDescriptor.getType();
                    String descriptorType = descriptor.getType();
                    if (currentType != null && currentType.equals(descriptorType)) {
                        // Types match - this is likely the default name
                        // Check if it's a type-only name (no components/parameters except __customName)
                        boolean isTypeOnly = currentDescriptor.getComponents().isEmpty()
                                && (currentDescriptor.getParameters().isEmpty() || currentDescriptor.getParameters()
                                        .keySet()
                                        .stream()
                                        .allMatch(k -> k.startsWith("__")));
                        if (isTypeOnly) {
                            // Type-only default name - this is the default, not a custom name
                            hasCustomName = false;
                        } else {
                            // Has components/parameters - compare more carefully
                            // Remove __customName from both for comparison
                            hasCustomName = !descriptorsEqualIgnoringLabel(currentDescriptor, descriptor);
                        }
                    }
                }
            } catch (Exception e) {
                // Parsing failed - treat as custom name
                hasCustomName = true;
            }
        }
        // If currentName doesn't start with "{", it's definitely a custom name

        if (hasCustomName) {
            // Rule has a custom name - preserve it
            // Store it in __customName parameter so it's preserved even when applyLabel()
            // is called
            Map<String, Object> params = new LinkedHashMap<>(descriptor.getParameters());
            params.put("__customName", currentName);
            builder.withParameters(params);
            // Also set as label for top-level rules (if label not already set)
            if (descriptor.getLabel() == null) {
                builder.withLabel(currentName);
            }
            descriptor = builder.build();
        }

        visited.put(rule, descriptor);
        return descriptor;
    }

    /**
     * Rebuilds a rule from a descriptor tree.
     *
     * @param series     series to use for {@link Num} reconstruction and indicator
     *                   factories
     * @param descriptor descriptor describing the rule
     * @return reconstructed rule
     */
    public static Rule fromDescriptor(BarSeries series, ComponentDescriptor descriptor) {
        return fromDescriptor(series, descriptor, null);
    }

    /**
     * Rebuilds a rule from a descriptor tree, optionally with a parent context for
     * resolving Strategy-level components.
     *
     * @param series        series to use for {@link Num} reconstruction and
     *                      indicator factories
     * @param descriptor    descriptor describing the rule
     * @param parentContext optional parent context for resolving shared components
     * @return reconstructed rule
     */
    public static Rule fromDescriptor(BarSeries series, ComponentDescriptor descriptor,
            ReconstructionContext parentContext) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(descriptor, "descriptor");

        String type = descriptor.getType();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Rule descriptor missing type: " + descriptor);
        }

        Class<?> clazz;
        try {
            clazz = Class.forName(type);
        } catch (ClassNotFoundException ex) {
            try {
                clazz = Class.forName("org.ta4j.core.rules." + type);
            } catch (ClassNotFoundException inner) {
                throw new IllegalArgumentException("Unknown rule type: " + type, inner);
            }
        }
        if (!Rule.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Descriptor type does not implement Rule: " + type);
        }

        @SuppressWarnings("unchecked")
        Class<? extends Rule> ruleType = (Class<? extends Rule>) clazz;

        // Infer constructor signature from children and parameters
        ReconstructionContext context = new ReconstructionContext(series, descriptor, parentContext);
        DeserializationMatch match = inferConstructor(ruleType, descriptor, context);

        try {
            try {
                match.constructor.setAccessible(true);
            } catch (SecurityException ex) {
                // SecurityManager may prevent changing accessibility.
                // Continue without changing accessibility - if the constructor is already
                // accessible, newInstance will succeed; otherwise it will throw
                // IllegalAccessException which is caught below.
            }
            Rule rule = match.constructor.newInstance(match.arguments);

            // Restore custom name if present
            // Custom names can be stored either as:
            // 1. The label (for top-level rules)
            // 2. The "__customName" parameter (for child rules, where label is used for
            // matching)
            String customName = null;
            Map<String, Object> params = descriptor.getParameters();
            if (params.containsKey("__customName")) {
                customName = String.valueOf(params.get("__customName"));
            } else {
                String label = descriptor.getLabel();
                if (label != null && !isSimpleIdentifier(label)) {
                    // Label is not a simple identifier, so it's likely a custom name
                    customName = label;
                }
            }

            if (customName != null) {
                rule.setName(customName);
            }

            return rule;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to construct rule: " + ruleType.getName(), ex);
        }
    }

    private static final class DeserializationMatch {
        final Constructor<? extends Rule> constructor;
        final Object[] arguments;
        final Class<?>[] parameterTypes;

        DeserializationMatch(Constructor<? extends Rule> constructor, Object[] arguments, Class<?>[] parameterTypes) {
            this.constructor = constructor;
            this.arguments = arguments;
            this.parameterTypes = parameterTypes;
        }
    }

    /**
     * Infers the constructor signature from components and parameters. Matches
     * components (indicators/rules) and parameters (numbers, strings, etc.) to
     * constructor parameters.
     */
    private static DeserializationMatch inferConstructor(Class<? extends Rule> ruleType, ComponentDescriptor descriptor,
            ReconstructionContext context) {
        List<ComponentDescriptor> components = descriptor.getComponents();
        Map<String, Object> parameters = descriptor.getParameters();

        // Filter out internal metadata parameters (enum types, etc.)
        Map<String, Object> filteredParams = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (!entry.getKey().startsWith("__")) {
                filteredParams.put(entry.getKey(), entry.getValue());
            }
        }

        // Try each constructor to find a match
        Constructor<?>[] constructors = ruleType.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            java.lang.reflect.Parameter[] params = constructor.getParameters();

            // Check if first parameter is BarSeries (common pattern)
            int startIndex = 0;
            if (paramTypes.length > 0 && paramTypes[0].equals(BarSeries.class)) {
                startIndex = 1;
            }

            // Try to match remaining parameters
            DeserializationMatch match = tryMatchConstructor(constructor, paramTypes, params, startIndex, components,
                    filteredParams, context);
            if (match != null) {
                @SuppressWarnings("unchecked")
                Constructor<? extends Rule> ruleConstructor = (Constructor<? extends Rule>) constructor;
                return new DeserializationMatch(ruleConstructor, match.arguments, match.parameterTypes);
            }
        }

        throw new IllegalStateException(
                buildConstructorNotFoundMessage(ruleType, components, filteredParams, constructors));
    }

    /**
     * Builds a descriptive error message when no compatible constructor is found.
     * The message includes details about what was found (components and parameters)
     * and what constructor signatures are available.
     */
    private static String buildConstructorNotFoundMessage(Class<? extends Rule> ruleType,
            List<ComponentDescriptor> components, Map<String, Object> filteredParams, Constructor<?>[] constructors) {
        StringBuilder msg = new StringBuilder();
        msg.append("No compatible constructor found for rule type: ").append(ruleType.getName());
        msg.append("\n  Found: ").append(components.size()).append(" component(s)");
        if (!components.isEmpty()) {
            msg.append(" [");
            for (int i = 0; i < components.size(); i++) {
                if (i > 0) {
                    msg.append(", ");
                }
                ComponentDescriptor comp = components.get(i);
                String type = comp != null && comp.getType() != null ? comp.getType() : "null";
                msg.append(type);
            }
            msg.append("]");
        }
        msg.append(", ").append(filteredParams.size()).append(" parameter(s)");
        if (!filteredParams.isEmpty()) {
            msg.append(" [");
            boolean first = true;
            for (Map.Entry<String, Object> entry : filteredParams.entrySet()) {
                if (!first) {
                    msg.append(", ");
                }
                first = false;
                msg.append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null");
            }
            msg.append("]");
        }
        msg.append("\n  Looking for a constructor that accepts: ");
        if (!components.isEmpty() || !filteredParams.isEmpty()) {
            List<String> expectedParams = new ArrayList<>();
            for (ComponentDescriptor comp : components) {
                if (comp != null && comp.getType() != null) {
                    if (comp.getType().contains("Rule")) {
                        expectedParams.add("Rule");
                    } else if (comp.getType().contains("Indicator")) {
                        expectedParams.add("Indicator<Num>");
                    } else {
                        expectedParams.add(comp.getType());
                    }
                }
            }
            for (Map.Entry<String, Object> entry : filteredParams.entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    Class<?> valueClass = value.getClass();
                    if (Num.class.isAssignableFrom(valueClass)) {
                        expectedParams.add("Num");
                    } else if (Number.class.isAssignableFrom(valueClass) || valueClass.isPrimitive()) {
                        expectedParams.add(valueClass.getSimpleName());
                    } else if (value instanceof String) {
                        expectedParams.add("String");
                    } else {
                        expectedParams.add(valueClass.getSimpleName());
                    }
                } else {
                    expectedParams.add(entry.getKey() + " (unknown type)");
                }
            }
            msg.append(String.join(", ", expectedParams));
        } else {
            msg.append("(no arguments)");
        }
        msg.append("\n  Available constructors:");
        for (Constructor<?> constructor : constructors) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            java.lang.reflect.Parameter[] params = constructor.getParameters();
            msg.append("\n    ");
            int startIndex = 0;
            if (paramTypes.length > 0 && paramTypes[0].equals(BarSeries.class)) {
                msg.append("(BarSeries");
                startIndex = 1;
                if (paramTypes.length > 1) {
                    msg.append(", ");
                }
            } else {
                msg.append("(");
            }
            for (int i = startIndex; i < paramTypes.length; i++) {
                if (i > startIndex) {
                    msg.append(", ");
                }
                msg.append(simplifyParameterType(paramTypes[i]));
                if (i < params.length && params[i].isNamePresent()) {
                    msg.append(" ").append(params[i].getName());
                }
            }
            msg.append(")");
        }
        return msg.toString();
    }

    /**
     * Simplifies a parameter type name for error messages.
     */
    private static String simplifyParameterType(Class<?> paramType) {
        if (paramType.isArray()) {
            return simplifyParameterType(paramType.getComponentType()) + "[]";
        }
        String packageName = paramType.getPackageName();
        if (packageName != null && (packageName.equals(CORE_PACKAGE) || packageName.equals(RULE_PACKAGE)
                || packageName.equals(INDICATOR_PACKAGE) || packageName.equals(NUM_PACKAGE)
                || packageName.equals(JAVA_LANG_PACKAGE))) {
            return paramType.getSimpleName();
        }
        return paramType.getName();
    }

    private static DeserializationMatch tryMatchConstructor(Constructor<?> constructor, Class<?>[] paramTypes,
            java.lang.reflect.Parameter[] params, int startIndex, List<ComponentDescriptor> components,
            Map<String, Object> parameters, ReconstructionContext context) {
        int paramCount = paramTypes.length - startIndex;
        int totalArgs = components.size() + parameters.size();

        // Must match total argument count
        if (paramCount != totalArgs) {
            return null;
        }

        Object[] arguments = new Object[paramTypes.length];
        Class<?>[] argumentTypes = new Class<?>[paramTypes.length];

        // Set BarSeries if present
        if (startIndex > 0) {
            arguments[0] = context.series;
            argumentTypes[0] = BarSeries.class;
        }

        // Track which components and parameters we've used
        boolean[] componentsUsed = new boolean[components.size()];
        java.util.Set<String> paramsUsed = new java.util.HashSet<>();

        // Try to match each constructor parameter
        for (int i = startIndex; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            String paramName = params[i].getName();

            // Try to match from components first (indicators/rules)
            boolean matched = false;
            for (int j = 0; j < components.size(); j++) {
                if (componentsUsed[j]) {
                    continue;
                }
                ComponentDescriptor component = components.get(j);
                if (component == null) {
                    continue;
                }

                // Check if component type matches parameter type
                if (isAssignableFrom(paramType, component)) {
                    Object componentValue = resolveComponent(component, paramType, context);
                    if (componentValue != null) {
                        arguments[i] = componentValue;
                        argumentTypes[i] = paramType;
                        componentsUsed[j] = true;
                        matched = true;
                        break;
                    }
                }
            }

            // If not matched from components, try parameters
            if (!matched) {
                // Try exact parameter name match first
                if (parameters.containsKey(paramName)) {
                    Object paramValue = resolveParameter(parameters.get(paramName), paramType, paramName, parameters,
                            context);
                    if (paramValue != null) {
                        arguments[i] = paramValue;
                        argumentTypes[i] = paramType;
                        paramsUsed.add(paramName);
                        matched = true;
                    }
                } else {
                    // Try to match by type from remaining parameters
                    for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                        if (paramsUsed.contains(entry.getKey())) {
                            continue;
                        }
                        Object paramValue = resolveParameter(entry.getValue(), paramType, entry.getKey(), parameters,
                                context);
                        if (paramValue != null) {
                            arguments[i] = paramValue;
                            argumentTypes[i] = paramType;
                            paramsUsed.add(entry.getKey());
                            matched = true;
                            break;
                        }
                    }
                }
            }

            if (!matched) {
                return null; // Can't match this constructor
            }
        }

        // Verify all components and parameters were used
        for (boolean used : componentsUsed) {
            if (!used) {
                return null;
            }
        }
        if (paramsUsed.size() != parameters.size()) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Constructor<? extends Rule> ruleConstructor = (Constructor<? extends Rule>) constructor;
        return new DeserializationMatch(ruleConstructor, arguments, argumentTypes);
    }

    private static boolean isAssignableFrom(Class<?> paramType, ComponentDescriptor child) {
        String childType = child.getType();
        if (childType == null) {
            return false;
        }

        // Check if child is an Indicator and paramType is Indicator
        if (childType.contains("Indicator") && !childType.contains("Rule")) {
            return Indicator.class.isAssignableFrom(paramType);
        }

        // Check if child is a Rule and paramType is Rule
        if (childType.contains("Rule")) {
            return Rule.class.isAssignableFrom(paramType);
        }

        return false;
    }

    private static Object resolveComponent(ComponentDescriptor component, Class<?> paramType,
            ReconstructionContext context) {
        try {
            if (Indicator.class.isAssignableFrom(paramType)) {
                if (component.getLabel() != null) {
                    try {
                        return context.resolveIndicator(component.getLabel());
                    } catch (IllegalArgumentException ignored) {
                        // Fall through to descriptor-based reconstruction if the label was
                        // stripped during JSON round-trip or doesn't map to a named component
                    }
                }
                return IndicatorSerialization.fromDescriptor(context.series, component);
            } else if (Rule.class.isAssignableFrom(paramType)) {
                // For rule components during constructor matching, we have the component
                // descriptor directly, so we should deserialize it. The component descriptor
                // contains all its nested components (like indicators), so we can deserialize
                // it independently. Label-based lookup is only needed for cross-references
                // in Strategy contexts, not for direct component matching.
                // Pass the current context as parent so nested components can resolve:
                // 1. Components from the current rule's context (for nested rules/indicators)
                // 2. Strategy-level components via the parent chain
                try {
                    return RuleSerialization.fromDescriptor(context.series, component, context);
                } catch (Exception e) {
                    return null; // Can't resolve, try next match
                }
            }
        } catch (Exception e) {
            return null; // Can't resolve, try next match
        }
        return null;
    }

    private static Object resolveParameter(Object value, Class<?> paramType, String paramName,
            Map<String, Object> allParams, ReconstructionContext context) {
        if (value == null) {
            return null;
        }

        try {
            // Handle BarSeries (shouldn't happen here, but just in case)
            if (paramType.equals(BarSeries.class)) {
                return context.series;
            }

            // Handle Boolean first (before primitive check, since boolean is primitive)
            if (paramType.equals(Boolean.class) || paramType.equals(boolean.class)) {
                return context.resolveBoolean(paramName);
            }

            // Handle Num
            if (paramType.equals(Num.class)) {
                return context.resolveNum(paramName);
            }

            // Handle numbers (but not boolean, which is already handled above)
            if (Number.class.isAssignableFrom(paramType)
                    || (paramType.isPrimitive() && !paramType.equals(boolean.class))) {
                return context.resolveNumber(paramName, paramType);
            }

            // Handle String
            if (paramType.equals(String.class)) {
                return context.resolveString(paramName);
            }

            // Handle Enum
            if (paramType.isEnum()) {
                String enumTypeKey = "__enumType_" + paramName;
                String enumTypeName = allParams.containsKey(enumTypeKey) ? String.valueOf(allParams.get(enumTypeKey))
                        : paramType.getName();
                return context.resolveEnum(paramName, enumTypeName);
            }

            // Handle arrays
            if (paramType.isArray()) {
                Class<?> componentType = paramType.getComponentType();
                if (Number.class.isAssignableFrom(componentType) || componentType.isPrimitive()) {
                    return context.resolveNumberArray(paramName, paramType);
                } else if (componentType.isEnum()) {
                    String enumTypeKey = "__enumType_" + paramName;
                    String enumTypeName = allParams.containsKey(enumTypeKey)
                            ? String.valueOf(allParams.get(enumTypeKey))
                            : componentType.getName();
                    return context.resolveEnumArray(paramName, enumTypeName);
                } else if (componentType.equals(ChainLink.class)) {
                    return deserializeChainLinks(value, context);
                }
            }
        } catch (Exception e) {
            return null; // Can't resolve, try next match
        }

        return null;
    }

    private static ChainLink[] deserializeChainLinks(Object raw, ReconstructionContext context) {
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("Chain link parameter must be a list but was " + raw);
        }
        ChainLink[] links = new ChainLink[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object entry = list.get(i);
            if (entry == null) {
                links[i] = null;
                continue;
            }
            if (!(entry instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Chain link entry must be an object but was " + entry);
            }
            Rule linkRule = null;
            Object ruleValue = map.get("rule");
            if (ruleValue != null) {
                ComponentDescriptor ruleDescriptor = parseChainLinkRule(ruleValue);
                if (ruleDescriptor != null) {
                    linkRule = RuleSerialization.fromDescriptor(context.series, ruleDescriptor, context);
                }
            }
            int threshold = 0;
            Object thresholdValue = map.get("threshold");
            if (thresholdValue != null) {
                Object converted = convertNumber(thresholdValue, Integer.class);
                if (converted instanceof Number number) {
                    threshold = number.intValue();
                } else {
                    threshold = Integer.parseInt(String.valueOf(converted));
                }
            }
            links[i] = new ChainLink(linkRule, threshold);
        }
        return links;
    }

    private static ComponentDescriptor parseChainLinkRule(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ComponentDescriptor descriptor) {
            return descriptor;
        }
        if (value instanceof String json) {
            return ComponentSerialization.parse(json);
        }
        throw new IllegalArgumentException("Unsupported chain link rule payload: " + value);
    }

    private static final class ArgumentContext {

        private final Map<String, Object> parameters;
        private final List<ComponentDescriptor> components;
        private final IdentityHashMap<Rule, ComponentDescriptor> visited;

        private ArgumentContext(Map<String, Object> parameters, List<ComponentDescriptor> components,
                IdentityHashMap<Rule, ComponentDescriptor> visited) {
            this.parameters = parameters;
            this.components = components;
            this.visited = visited;
        }
    }

    static final class ReconstructionContext {

        private final BarSeries series;
        private final ComponentDescriptor descriptor;
        private final Map<String, ComponentDescriptor> componentsByLabel;
        private final ReconstructionContext parentContext;

        ReconstructionContext(BarSeries series, ComponentDescriptor descriptor) {
            this(series, descriptor, null);
        }

        ReconstructionContext(BarSeries series, ComponentDescriptor descriptor, ReconstructionContext parentContext) {
            this.series = series;
            this.descriptor = descriptor;
            this.parentContext = parentContext;
            if (descriptor.getComponents().isEmpty()) {
                this.componentsByLabel = Collections.emptyMap();
            } else {
                Map<String, ComponentDescriptor> map = new LinkedHashMap<>();
                for (ComponentDescriptor component : descriptor.getComponents()) {
                    if (component != null && component.getLabel() != null) {
                        map.put(component.getLabel(), component);
                    }
                }
                this.componentsByLabel = map;
            }
        }

        private Rule resolveRule(String label) {
            ComponentDescriptor component = componentsByLabel.get(label);
            if (component == null) {
                // Check parent context if available (for Strategy-level components)
                if (parentContext != null) {
                    return parentContext.resolveRule(label);
                }
                throw new IllegalArgumentException("Missing rule component descriptor: " + label);
            }
            // Pass the current context as parent so nested components can resolve
            // Strategy-level components
            return RuleSerialization.fromDescriptor(series, component, this);
        }

        private Indicator<?> resolveIndicator(String label) {
            ComponentDescriptor component = componentsByLabel.get(label);
            if (component == null) {
                // Check parent context if available (for Strategy-level components)
                if (parentContext != null) {
                    return parentContext.resolveIndicator(label);
                }
                throw new IllegalArgumentException("Missing indicator component descriptor: " + label);
            }
            // For indicators, we don't need to pass context since they don't have nested
            // components
            // that need Strategy-level resolution
            return IndicatorSerialization.fromDescriptor(series, component);
        }

        private Num resolveNum(String name) {
            Object value = descriptor.getParameters().get(name);
            if (value == null) {
                throw new IllegalArgumentException("Missing numeric parameter: " + name);
            }
            return series.numFactory().numOf(String.valueOf(value));
        }

        private Object resolveNumber(String name, Class<?> targetType) {
            Object raw = descriptor.getParameters().get(name);
            if (raw == null) {
                throw new IllegalArgumentException("Missing numeric parameter: " + name);
            }
            return convertNumber(raw, targetType);
        }

        private Object resolveNumberArray(String name, Class<?> targetType) {
            Object raw = descriptor.getParameters().get(name);
            if (!(raw instanceof List<?> list)) {
                throw new IllegalArgumentException("Missing numeric array parameter: " + name);
            }
            Class<?> componentType = targetType.getComponentType();
            Object array = Array.newInstance(componentType, list.size());
            for (int i = 0; i < list.size(); i++) {
                Object element = list.get(i);
                Object converted = convertNumber(element, componentType);
                Array.set(array, i, converted);
            }
            return array;
        }

        private String resolveString(String name) {
            Object value = descriptor.getParameters().get(name);
            return value == null ? null : String.valueOf(value);
        }

        private Boolean resolveBoolean(String name) {
            Object value = descriptor.getParameters().get(name);
            if (value == null) {
                throw new IllegalArgumentException("Missing boolean parameter: " + name);
            }
            return (Boolean) convertBoolean(value);
        }

        private Object resolveEnum(String name, String enumClassName) {
            Object raw = descriptor.getParameters().get(name);
            if (raw == null) {
                throw new IllegalArgumentException("Missing enum parameter: " + name);
            }
            try {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                Class<? extends Enum> enumType = (Class<? extends Enum>) resolveClass(enumClassName);
                String label = String.valueOf(raw);
                return Enum.valueOf(enumType, label);
            } catch (IllegalStateException ex) {
                throw new IllegalStateException("Unable to resolve enum type: " + enumClassName, ex);
            }
        }

        private Object resolveEnumArray(String name, String enumClassName) {
            Object raw = descriptor.getParameters().get(name);
            if (!(raw instanceof List<?> list)) {
                throw new IllegalArgumentException("Missing enum array parameter: " + name);
            }
            try {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                Class<? extends Enum> enumType = (Class<? extends Enum>) resolveClass(enumClassName);
                Object array = Array.newInstance(enumType, list.size());
                for (int i = 0; i < list.size(); i++) {
                    Object element = list.get(i);
                    Object value = element == null ? null : Enum.valueOf(enumType, String.valueOf(element));
                    Array.set(array, i, value);
                }
                return array;
            } catch (IllegalStateException ex) {
                throw new IllegalStateException("Unable to resolve enum type: " + enumClassName, ex);
            }
        }

        private Class<?> resolveClass(String typeName) {
            return switch (typeName) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "char" -> char.class;
            default -> {
                try {
                    // Try as-is first (for fully qualified names or already resolved simple names)
                    yield Class.forName(typeName);
                } catch (ClassNotFoundException ex) {
                    // Try common packages for simple names
                    String[] packages = { CORE_PACKAGE, RULE_PACKAGE, INDICATOR_PACKAGE, NUM_PACKAGE,
                            JAVA_LANG_PACKAGE };
                    for (String pkg : packages) {
                        try {
                            yield Class.forName(pkg + "." + typeName);
                        } catch (ClassNotFoundException ignored) {
                            // Continue to next package
                        }
                    }
                    throw new IllegalStateException("Unable to resolve argument type: " + typeName, ex);
                }
            }
            };
        }
    }

    private static Object convertBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Object convertNumber(Object value, Class<?> targetType) {
        if (targetType.equals(Number.class) || targetType.equals(Object.class)) {
            if (value instanceof Number) {
                return value;
            }
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Failed to convert value '" + value + "' to Double: " + e.getMessage(), e);
            }
        }
        if (targetType.equals(int.class) || targetType.equals(Integer.class)) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Failed to convert value '" + value + "' to Integer: " + e.getMessage(), e);
            }
        }
        if (targetType.equals(long.class) || targetType.equals(Long.class)) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Failed to convert value '" + value + "' to Long: " + e.getMessage(),
                        e);
            }
        }
        if (targetType.equals(double.class) || targetType.equals(Double.class)) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Failed to convert value '" + value + "' to Double: " + e.getMessage(), e);
            }
        }
        if (targetType.equals(float.class) || targetType.equals(Float.class)) {
            if (value instanceof Number number) {
                return number.floatValue();
            }
            try {
                return Float.parseFloat(String.valueOf(value));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Failed to convert value '" + value + "' to Float: " + e.getMessage(), e);
            }
        }
        if (targetType.equals(short.class) || targetType.equals(Short.class)) {
            if (value instanceof Number number) {
                return number.shortValue();
            }
            try {
                return Short.parseShort(String.valueOf(value));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Failed to convert value '" + value + "' to Short: " + e.getMessage(), e);
            }
        }
        if (targetType.equals(byte.class) || targetType.equals(Byte.class)) {
            if (value instanceof Number number) {
                return number.byteValue();
            }
            try {
                return Byte.parseByte(String.valueOf(value));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Failed to convert value '" + value + "' to Byte: " + e.getMessage(),
                        e);
            }
        }
        throw new IllegalStateException("Unsupported numeric target type: " + targetType.getName());
    }

    private static final class ConstructorMatch {

        private final Constructor<?> constructor;
        private final List<Argument> arguments;

        private ConstructorMatch(Constructor<?> constructor, List<Argument> arguments) {
            this.constructor = constructor;
            this.arguments = arguments;
        }

        private static ConstructorMatch locate(Rule rule) {
            Constructor<?>[] constructors = rule.getClass().getDeclaredConstructors();
            List<Constructor<?>> ordered = new ArrayList<>(constructors.length);
            Collections.addAll(ordered, constructors);
            ordered.sort((left, right) -> Integer.compare(right.getParameterCount(), left.getParameterCount()));

            Map<String, Object> values = FieldExtractor.extract(rule);
            for (Constructor<?> constructor : ordered) {
                Optional<List<Argument>> arguments = match(rule, constructor, values);
                if (arguments.isPresent()) {
                    return new ConstructorMatch(constructor, arguments.get());
                }
            }
            return null;
        }

        private static Optional<List<Argument>> match(Rule rule, Constructor<?> constructor,
                Map<String, Object> values) {
            Parameter[] parameters = constructor.getParameters();
            List<Argument> arguments = new ArrayList<>(parameters.length);
            Set<String> used = new LinkedHashSet<>();

            for (int index = 0; index < parameters.length; index++) {
                Parameter parameter = parameters[index];
                Class<?> type = parameter.getType();
                String name = parameterName(parameter, index);

                if (BarSeries.class.isAssignableFrom(type)) {
                    arguments.add(Argument.series(name, type));
                    continue;
                }

                if (Rule.class.isAssignableFrom(type)) {
                    Match match = findMatch(values, used, value -> value instanceof Rule && value != rule);
                    if (match == null) {
                        return Optional.empty();
                    }
                    arguments.add(Argument.rule(name, type, (Rule) match.value, match.label()));
                    continue;
                }

                if (Indicator.class.isAssignableFrom(type)) {
                    Match match = findIndicatorMatch(values, used, parameter);
                    if (match == null) {
                        return Optional.empty();
                    }
                    arguments.add(Argument.indicator(name, type, (Indicator<?>) match.value, match.label()));
                    continue;
                }

                if (Num.class.isAssignableFrom(type)) {
                    Match match = findMatch(values, used, Num.class::isInstance);
                    if (match == null) {
                        return Optional.empty();
                    }
                    arguments.add(Argument.num(name, type, (Num) match.value));
                    continue;
                }

                if (type.isArray()) {
                    Match match = findArrayMatch(values, used, type);
                    if (match == null) {
                        return Optional.empty();
                    }
                    Class<?> componentType = type.getComponentType();
                    if (componentType.isEnum()) {
                        arguments.add(Argument.enumArray(name, type, match.value));
                        continue;
                    }
                    if (isNumericType(componentType)) {
                        arguments.add(Argument.numberArray(name, type, match.value));
                        continue;
                    }
                    if (componentType.equals(ChainLink.class)) {
                        arguments.add(Argument.chainLinks(name, (ChainLink[]) match.value));
                        continue;
                    }
                    return Optional.empty();
                }

                if (type.isEnum()) {
                    Match match = findMatch(values, used, type::isInstance);
                    if (match == null) {
                        return Optional.empty();
                    }
                    @SuppressWarnings("unchecked")
                    Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) type;
                    arguments.add(Argument.enumValue(name, enumType, (Enum<?>) match.value));
                    continue;
                }

                if (type.equals(String.class)) {
                    Match match = findMatch(values, used, value -> value instanceof String);
                    if (match == null) {
                        return Optional.empty();
                    }
                    arguments.add(Argument.string(name, match.value));
                    continue;
                }

                if (type.equals(boolean.class) || type.equals(Boolean.class)) {
                    Match match = findMatch(values, used, value -> value instanceof Boolean);
                    if (match == null) {
                        return Optional.empty();
                    }
                    arguments.add(Argument.bool(name, type, (Boolean) match.value));
                    continue;
                }

                if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
                    Match match = findNumericMatch(values, used);
                    if (match == null) {
                        return Optional.empty();
                    }
                    arguments.add(Argument.number(name, type, match.value));
                    continue;
                }

                return Optional.empty();
            }

            return Optional.of(arguments);
        }

        private static String parameterName(Parameter parameter, int index) {
            if (parameter.isNamePresent()) {
                return parameter.getName();
            }
            return "arg" + index;
        }

        private static Match findIndicatorMatch(Map<String, Object> values, Set<String> used, Parameter parameter) {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (used.contains(entry.getKey())) {
                    continue;
                }
                Object value = entry.getValue();
                if (!(value instanceof Indicator<?> indicator)) {
                    continue;
                }
                if (!indicatorAccepts(parameter, indicator)) {
                    continue;
                }
                used.add(entry.getKey());
                return new Match(entry.getKey(), indicator);
            }
            return null;
        }

        private static boolean indicatorAccepts(Parameter parameter, Indicator<?> indicator) {
            Type parameterized = parameter.getParameterizedType();
            if (parameterized instanceof ParameterizedType type) {
                Type[] arguments = type.getActualTypeArguments();
                if (arguments.length == 1) {
                    Type argument = arguments[0];
                    if (argument instanceof Class<?> clazz) {
                        Class<?> actual = IndicatorIntrospector.resolveValueType(indicator.getClass());
                        if (actual != null && !clazz.isAssignableFrom(actual)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private static Match findNumericMatch(Map<String, Object> values, Set<String> used) {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (used.contains(entry.getKey())) {
                    continue;
                }
                Object value = entry.getValue();
                if (value instanceof Number || value instanceof Num) {
                    used.add(entry.getKey());
                    return new Match(entry.getKey(), value);
                }
            }
            return null;
        }

        private static Match findMatch(Map<String, Object> values, Set<String> used,
                java.util.function.Predicate<Object> filter) {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (used.contains(entry.getKey())) {
                    continue;
                }
                Object value = entry.getValue();
                if (value != null && filter.test(value)) {
                    used.add(entry.getKey());
                    return new Match(entry.getKey(), value);
                }
            }
            return null;
        }

        private static Match findArrayMatch(Map<String, Object> values, Set<String> used, Class<?> arrayType) {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (used.contains(entry.getKey())) {
                    continue;
                }
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }
                if (arrayType.isInstance(value)) {
                    used.add(entry.getKey());
                    return new Match(entry.getKey(), copyArray(value, arrayType));
                }
                if (value instanceof Collection<?> collection) {
                    Class<?> componentType = arrayType.getComponentType();
                    if (componentType != null && !componentType.isPrimitive()) {
                        Object array = collectionToArray(collection, componentType);
                        if (array != null) {
                            used.add(entry.getKey());
                            return new Match(entry.getKey(), array);
                        }
                    }
                }
            }
            return null;
        }

        private static boolean isNumericType(Class<?> type) {
            if (type.isPrimitive()) {
                return type.equals(int.class) || type.equals(long.class) || type.equals(double.class)
                        || type.equals(float.class) || type.equals(short.class) || type.equals(byte.class);
            }
            return Number.class.isAssignableFrom(type);
        }

        private static Object copyArray(Object source, Class<?> arrayType) {
            int length = Array.getLength(source);
            Object copy = Array.newInstance(arrayType.getComponentType(), length);
            System.arraycopy(source, 0, copy, 0, length);
            return copy;
        }

        private static Object collectionToArray(Collection<?> collection, Class<?> componentType) {
            Object array = Array.newInstance(componentType, collection.size());
            int index = 0;
            for (Object element : collection) {
                if (element != null && !componentType.isInstance(element)) {
                    return null;
                }
                Array.set(array, index++, element);
            }
            return array;
        }
    }

    private record Match(String key, Object value) {
        private String label() {
            int lastDot = key.lastIndexOf('.');
            return lastDot >= 0 ? key.substring(lastDot + 1) : key;
        }
    }

    private enum ArgumentKind {
        SERIES, RULE, INDICATOR, NUM, NUMBER, INT, LONG, DOUBLE, BOOLEAN, STRING, ENUM, NUMBER_ARRAY, INT_ARRAY,
        LONG_ARRAY, DOUBLE_ARRAY, ENUM_ARRAY, CHAIN_LINKS
    }

    private static final class Argument {

        private final ArgumentKind kind;
        private final String name;
        private final Class<?> targetType;
        private final Object value;
        private final String label;

        private Argument(ArgumentKind kind, String name, Class<?> targetType, Object value, String label) {
            this.kind = kind;
            this.name = name;
            this.targetType = targetType;
            this.value = value;
            this.label = label;
        }

        private static Argument series(String name, Class<?> targetType) {
            return new Argument(ArgumentKind.SERIES, name, targetType, null, name);
        }

        private static Argument rule(String name, Class<?> targetType, Rule rule, String label) {
            return new Argument(ArgumentKind.RULE, name, targetType, rule, label);
        }

        private static Argument indicator(String name, Class<?> targetType, Indicator<?> indicator, String label) {
            return new Argument(ArgumentKind.INDICATOR, name, targetType, indicator, label);
        }

        private static Argument num(String name, Class<?> targetType, Num value) {
            return new Argument(ArgumentKind.NUM, name, targetType, value, name);
        }

        private static Argument chainLinks(String name, ChainLink[] value) {
            return new Argument(ArgumentKind.CHAIN_LINKS, name, ChainLink[].class, value, name);
        }

        private static Argument enumValue(String name, Class<? extends Enum<?>> targetType, Enum<?> value) {
            return new Argument(ArgumentKind.ENUM, name, targetType, value, name);
        }

        private static Argument string(String name, Object value) {
            return new Argument(ArgumentKind.STRING, name, String.class, value, name);
        }

        private static Argument bool(String name, Class<?> targetType, Boolean value) {
            return new Argument(ArgumentKind.BOOLEAN, name, targetType, value, name);
        }

        private static Argument number(String name, Class<?> targetType, Object value) {
            ArgumentKind kind = determineNumericKind(targetType);
            return new Argument(kind, name, targetType, value, name);
        }

        private static Argument numberArray(String name, Class<?> targetType, Object value) {
            ArgumentKind kind = determineNumericArrayKind(targetType.getComponentType());
            return new Argument(kind, name, targetType, value, name);
        }

        private static Argument enumArray(String name, Class<?> targetType, Object value) {
            return new Argument(ArgumentKind.ENUM_ARRAY, name, targetType, value, name);
        }

        private static ArgumentKind determineNumericKind(Class<?> targetType) {
            if (targetType.equals(int.class) || targetType.equals(Integer.class)) {
                return ArgumentKind.INT;
            }
            if (targetType.equals(long.class) || targetType.equals(Long.class)) {
                return ArgumentKind.LONG;
            }
            if (targetType.equals(double.class) || targetType.equals(Double.class)) {
                return ArgumentKind.DOUBLE;
            }
            return ArgumentKind.NUMBER;
        }

        private static ArgumentKind determineNumericArrayKind(Class<?> componentType) {
            if (componentType.equals(int.class) || componentType.equals(Integer.class)) {
                return ArgumentKind.INT_ARRAY;
            }
            if (componentType.equals(long.class) || componentType.equals(Long.class)) {
                return ArgumentKind.LONG_ARRAY;
            }
            if (componentType.equals(double.class) || componentType.equals(Double.class)) {
                return ArgumentKind.DOUBLE_ARRAY;
            }
            return ArgumentKind.NUMBER_ARRAY;
        }

        private void serialize(ArgumentContext context) {
            // Serialize components and parameters; deserialization infers constructor
            // signature from these
            switch (kind) {
            case SERIES:
                // Series is passed implicitly, not serialized
                break;
            case RULE:
                Rule rule = (Rule) value;
                ComponentDescriptor ruleDescriptor = RuleSerialization.describe(rule, context.visited);
                context.components.add(applyLabel(ruleDescriptor, label));
                break;
            case INDICATOR:
                Indicator<?> indicator = (Indicator<?>) value;
                ComponentDescriptor indicatorDescriptor = IndicatorSerialization.describe(indicator);
                // Apply label for matching during deserialization
                ComponentDescriptor labeledDescriptor = applyLabel(indicatorDescriptor, label);
                context.components.add(labeledDescriptor);
                break;
            case NUM:
                context.parameters.put(name, value == null ? null : String.valueOf(value));
                break;
            case ENUM:
                Enum<?> enumValue = (Enum<?>) value;
                context.parameters.put(name, enumValue == null ? null : enumValue.name());
                // Store enum type in parameter name with special prefix for deserialization
                context.parameters.put("__enumType_" + name, simplifyClassName(targetType));
                break;
            case STRING:
                context.parameters.put(name, value);
                break;
            case BOOLEAN:
                context.parameters.put(name, value);
                break;
            case NUMBER:
            case INT:
            case LONG:
            case DOUBLE:
                context.parameters.put(name, serializeNumber(value));
                break;
            case NUMBER_ARRAY:
            case INT_ARRAY:
            case LONG_ARRAY:
            case DOUBLE_ARRAY:
                context.parameters.put(name, serializeNumberArray(value));
                break;
            case ENUM_ARRAY:
                // Value is an array, not a List
                List<String> serialized = serializeEnumArray(value);
                context.parameters.put(name, serialized);
                // Store enum type in parameter name with special prefix for deserialization
                Class<?> componentType = targetType.getComponentType();
                if (componentType != null) {
                    context.parameters.put("__enumType_" + name, simplifyClassName(componentType));
                }
                break;
            case CHAIN_LINKS:
                context.parameters.put(name, serializeChainLinks((ChainLink[]) value, context));
                break;
            default:
                throw new IllegalStateException("Unsupported argument kind: " + kind);
            }
        }

        private static Object serializeNumber(Object value) {
            if (value instanceof Num num) {
                return String.valueOf(num);
            }
            return value;
        }

        private static List<Object> serializeNumberArray(Object array) {
            int length = Array.getLength(array);
            List<Object> serialized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(array, i);
                serialized.add(serializeNumber(element));
            }
            return serialized;
        }

        private static List<String> serializeEnumArray(Object array) {
            int length = Array.getLength(array);
            List<String> serialized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(array, i);
                if (element == null) {
                    serialized.add(null);
                } else {
                    Enum<?> enumValue = (Enum<?>) element;
                    serialized.add(enumValue.name());
                }
            }
            return serialized;
        }

        private static List<Map<String, Object>> serializeChainLinks(ChainLink[] links, ArgumentContext context) {
            List<Map<String, Object>> serialized = new ArrayList<>(links.length);
            for (ChainLink link : links) {
                if (link == null) {
                    serialized.add(null);
                    continue;
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("threshold", serializeNumber(link.getThreshold()));
                Rule linkRule = link.getRule();
                if (linkRule != null) {
                    ComponentDescriptor descriptor = RuleSerialization.describe(linkRule, context.visited);
                    payload.put("rule", ComponentSerialization.toJson(descriptor));
                } else {
                    payload.put("rule", null);
                }
                serialized.add(payload);
            }
            return serialized;
        }
    }

    private static ComponentDescriptor applyLabel(ComponentDescriptor descriptor, String label) {
        if (descriptor == null) {
            return null;
        }
        ComponentDescriptor.Builder builder = ComponentDescriptor.builder()
                .withType(descriptor.getType())
                .withLabel(label);
        if (!descriptor.getParameters().isEmpty()) {
            builder.withParameters(descriptor.getParameters());
        }
        for (ComponentDescriptor component : descriptor.getComponents()) {
            builder.addComponent(component);
        }
        ComponentDescriptor result = builder.build();

        // Preserve __customName if it exists in the original descriptor
        // (it was set by describe() when the rule had a custom name)
        if (descriptor.getParameters().containsKey("__customName")) {
            Map<String, Object> params = new LinkedHashMap<>(result.getParameters());
            params.put("__customName", descriptor.getParameters().get("__customName"));
            builder.withParameters(params);
            result = builder.build();
        }

        return result;
    }

    /**
     * Checks if a string is a simple identifier (like "rule1", "entry", "exit")
     * used for parameter matching, as opposed to a custom name.
     */
    private static boolean isSimpleIdentifier(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // Simple identifiers are typically: start with letter, followed by
        // letters/digits
        // and don't contain special characters like '{', '[', etc. that appear in JSON
        return str.matches("^[a-zA-Z][a-zA-Z0-9]*$") && !str.startsWith("{") && !str.startsWith("[");
    }

    /**
     * Compares two ComponentDescriptor objects for equality, ignoring the label
     * field. Used to determine if a rule's name matches the default name.
     */
    private static boolean descriptorsEqualIgnoringLabel(ComponentDescriptor d1, ComponentDescriptor d2) {
        if (d1 == d2) {
            return true;
        }
        if (d1 == null || d2 == null) {
            return false;
        }
        // Compare type
        if (!Objects.equals(d1.getType(), d2.getType())) {
            return false;
        }
        // Compare parameters (ignoring __customName which we add)
        Map<String, Object> params1 = new LinkedHashMap<>(d1.getParameters());
        params1.remove("__customName");
        Map<String, Object> params2 = new LinkedHashMap<>(d2.getParameters());
        params2.remove("__customName");
        if (!Objects.equals(params1, params2)) {
            return false;
        }
        // Compare components (recursively, ignoring labels)
        List<ComponentDescriptor> comps1 = d1.getComponents();
        List<ComponentDescriptor> comps2 = d2.getComponents();
        if (comps1.size() != comps2.size()) {
            return false;
        }
        for (int i = 0; i < comps1.size(); i++) {
            if (!descriptorsEqualIgnoringLabel(comps1.get(i), comps2.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static final class FieldExtractor {

        private static Map<String, Object> extract(Rule rule) {
            Map<String, Object> values = new LinkedHashMap<>();
            Class<?> type = rule.getClass();
            while (type != null && !type.equals(Object.class)) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())
                            || field.isSynthetic()) {
                        continue;
                    }
                    if (field.getDeclaringClass().equals(org.ta4j.core.rules.AbstractRule.class)) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value;
                    try {
                        value = field.get(rule);
                    } catch (IllegalAccessException ex) {
                        continue;
                    }
                    if (value == null) {
                        continue;
                    }
                    if (shouldIgnore(field.getName())) {
                        continue;
                    }
                    String key = field.getName();
                    values.put(key, value);
                    if (value instanceof CrossIndicator cross) {
                        values.put(key + ".low", cross.getLow());
                        values.put(key + ".up", cross.getUp());
                    }
                }
                type = type.getSuperclass();
            }
            return values;
        }

        private static boolean shouldIgnore(String name) {
            // Ignore constants (all uppercase field names)
            return name.equals(name.toUpperCase());
        }
    }

    private static final class IndicatorIntrospector {

        private static Class<?> resolveValueType(Class<?> indicatorType) {
            for (Class<?> current = indicatorType; current != null
                    && !current.equals(Object.class); current = current.getSuperclass()) {
                Type generic = current.getGenericSuperclass();
                if (generic instanceof ParameterizedType parameterized) {
                    Type raw = parameterized.getRawType();
                    if (raw instanceof Class<?> rawClass && Indicator.class.isAssignableFrom(rawClass)) {
                        Type[] arguments = parameterized.getActualTypeArguments();
                        if (arguments.length == 1 && arguments[0] instanceof Class<?> valueType) {
                            return valueType;
                        }
                    }
                }
            }
            return null;
        }
    }
}
