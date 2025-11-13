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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.helpers.CrossIndicator;
import org.ta4j.core.num.Num;

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

    private static final String ARGUMENTS_KEY = "__args";
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
        List<ComponentDescriptor> children = new ArrayList<>();

        ArgumentContext context = new ArgumentContext(parameters, children, visited);
        for (Argument argument : match.arguments) {
            argument.serialize(rule, context);
        }

        // No longer serialize __args metadata - deserialization will infer from
        // children and parameters
        if (!parameters.isEmpty()) {
            builder.withParameters(parameters);
        }
        for (ComponentDescriptor child : children) {
            builder.addChild(child);
        }

        ComponentDescriptor descriptor = builder.build();
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
        ReconstructionContext context = new ReconstructionContext(series, descriptor);
        DeserializationMatch match = inferConstructor(ruleType, descriptor, context);

        try {
            match.constructor.setAccessible(true);
            return match.constructor.newInstance(match.arguments);
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
     * Infers the constructor signature from children and parameters. Matches
     * children (indicators/rules) and parameters (numbers, strings, etc.) to
     * constructor parameters.
     */
    private static DeserializationMatch inferConstructor(Class<? extends Rule> ruleType, ComponentDescriptor descriptor,
            ReconstructionContext context) {
        List<ComponentDescriptor> children = descriptor.getChildren();
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
            DeserializationMatch match = tryMatchConstructor(constructor, paramTypes, params, startIndex, children,
                    filteredParams, context);
            if (match != null) {
                @SuppressWarnings("unchecked")
                Constructor<? extends Rule> ruleConstructor = (Constructor<? extends Rule>) constructor;
                return new DeserializationMatch(ruleConstructor, match.arguments, match.parameterTypes);
            }
        }

        throw new IllegalStateException("No compatible constructor found for rule type: " + ruleType.getName()
                + " with " + children.size() + " children and " + filteredParams.size() + " parameters");
    }

    private static DeserializationMatch tryMatchConstructor(Constructor<?> constructor, Class<?>[] paramTypes,
            java.lang.reflect.Parameter[] params, int startIndex, List<ComponentDescriptor> children,
            Map<String, Object> parameters, ReconstructionContext context) {
        int paramCount = paramTypes.length - startIndex;
        int totalArgs = children.size() + parameters.size();

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

        // Track which children and parameters we've used
        boolean[] childrenUsed = new boolean[children.size()];
        java.util.Set<String> paramsUsed = new java.util.HashSet<>();

        // Try to match each constructor parameter
        for (int i = startIndex; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            String paramName = params[i].getName();

            // Try to match from children first (indicators/rules)
            boolean matched = false;
            for (int j = 0; j < children.size(); j++) {
                if (childrenUsed[j]) {
                    continue;
                }
                ComponentDescriptor child = children.get(j);
                if (child == null) {
                    continue;
                }

                // Check if child type matches parameter type
                if (isAssignableFrom(paramType, child, context)) {
                    Object childValue = resolveChild(child, paramType, context);
                    if (childValue != null) {
                        arguments[i] = childValue;
                        argumentTypes[i] = paramType;
                        childrenUsed[j] = true;
                        matched = true;
                        break;
                    }
                }
            }

            // If not matched from children, try parameters
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

        // Verify all children and parameters were used
        for (boolean used : childrenUsed) {
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

    private static boolean isAssignableFrom(Class<?> paramType, ComponentDescriptor child,
            ReconstructionContext context) {
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

    private static Object resolveChild(ComponentDescriptor child, Class<?> paramType, ReconstructionContext context) {
        try {
            if (Indicator.class.isAssignableFrom(paramType)) {
                // Resolve indicator (position-based if no label)
                if (child.getLabel() == null) {
                    return context.resolveIndicatorByPosition();
                } else {
                    return context.resolveIndicator(child.getLabel());
                }
            } else if (Rule.class.isAssignableFrom(paramType)) {
                // Resolve rule (by label)
                if (child.getLabel() == null) {
                    throw new IllegalArgumentException("Rule child missing label: " + child);
                }
                return context.resolveRule(child.getLabel());
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
                return context.resolveEnum(paramName, enumTypeName, paramType);
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
                    return context.resolveEnumArray(paramName, enumTypeName, paramType);
                }
            }
        } catch (Exception e) {
            return null; // Can't resolve, try next match
        }

        return null;
    }

    private static final class ArgumentContext {

        private final Map<String, Object> parameters;
        private final List<ComponentDescriptor> children;
        private final IdentityHashMap<Rule, ComponentDescriptor> visited;

        private ArgumentContext(Map<String, Object> parameters, List<ComponentDescriptor> children,
                IdentityHashMap<Rule, ComponentDescriptor> visited) {
            this.parameters = parameters;
            this.children = children;
            this.visited = visited;
        }
    }

    private static final class ReconstructionContext {

        private final BarSeries series;
        private final ComponentDescriptor descriptor;
        private final Map<String, ComponentDescriptor> childrenByLabel;
        private final List<ComponentDescriptor> childrenByIndex;
        private int indicatorMatchIndex = 0; // Track position for matching indicators without labels

        private ReconstructionContext(BarSeries series, ComponentDescriptor descriptor) {
            this.series = series;
            this.descriptor = descriptor;
            if (descriptor.getChildren().isEmpty()) {
                this.childrenByLabel = Collections.emptyMap();
                this.childrenByIndex = Collections.emptyList();
            } else {
                Map<String, ComponentDescriptor> map = new LinkedHashMap<>();
                List<ComponentDescriptor> indexList = new ArrayList<>();
                for (ComponentDescriptor child : descriptor.getChildren()) {
                    if (child != null) {
                        indexList.add(child);
                        if (child.getLabel() != null) {
                            map.put(child.getLabel(), child);
                        }
                    } else {
                        indexList.add(null);
                    }
                }
                this.childrenByLabel = map;
                this.childrenByIndex = indexList;
            }
        }

        private Object resolveArgument(ArgumentKind kind, String name, Map<String, Object> metadata,
                Class<?> targetType) {
            switch (kind) {
            case SERIES:
                return series;
            case RULE: {
                String label = metadata.containsKey("label") ? String.valueOf(metadata.get("label")) : name;
                return resolveRule(label);
            }
            case INDICATOR: {
                String label = metadata.containsKey("label") ? String.valueOf(metadata.get("label")) : name;
                return resolveIndicator(label);
            }
            case NUM:
                return resolveNum(name);
            case NUMBER:
            case INT:
            case LONG:
            case DOUBLE:
                return resolveNumber(name, targetType);
            case NUMBER_ARRAY:
            case INT_ARRAY:
            case LONG_ARRAY:
            case DOUBLE_ARRAY:
                return resolveNumberArray(name, targetType);
            case BOOLEAN:
                return convertBoolean(descriptor.getParameters().get(name));
            case STRING:
                Object value = descriptor.getParameters().get(name);
                return value == null ? null : String.valueOf(value);
            case ENUM:
                String enumClassName = String.valueOf(metadata.get("enumType"));
                return resolveEnum(name, enumClassName, targetType);
            case ENUM_ARRAY:
                String enumArrayType = String.valueOf(metadata.get("enumType"));
                return resolveEnumArray(name, enumArrayType, targetType);
            default:
                throw new IllegalStateException("Unsupported argument kind: " + kind);
            }
        }

        private Rule resolveRule(String label) {
            ComponentDescriptor child = childrenByLabel.get(label);
            if (child == null) {
                throw new IllegalArgumentException("Missing child rule descriptor: " + label);
            }
            return RuleSerialization.fromDescriptor(series, child);
        }

        private Indicator<?> resolveIndicator(String label) {
            ComponentDescriptor child = childrenByLabel.get(label);
            if (child == null) {
                throw new IllegalArgumentException("Missing child indicator descriptor: " + label);
            }
            return IndicatorSerialization.fromDescriptor(series, child);
        }

        /**
         * Resolves an indicator by position (for indicators without labels). Finds the
         * next unused indicator child without a label.
         */
        private Indicator<?> resolveIndicatorByPosition() {
            for (int i = indicatorMatchIndex; i < childrenByIndex.size(); i++) {
                ComponentDescriptor candidate = childrenByIndex.get(i);
                if (candidate != null && candidate.getLabel() == null) {
                    // Check if this is an indicator (type contains "Indicator" but not "Rule")
                    String type = candidate.getType();
                    if (type != null && type.contains("Indicator") && !type.contains("Rule")) {
                        indicatorMatchIndex = i + 1; // Mark as used
                        return IndicatorSerialization.fromDescriptor(series, candidate);
                    }
                }
            }
            throw new IllegalArgumentException("Missing child indicator descriptor (position-based match failed)");
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

        private Object resolveEnum(String name, String enumClassName, Class<?> targetType) {
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

        private Object resolveEnumArray(String name, String enumClassName, Class<?> targetType) {
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
            return Double.parseDouble(String.valueOf(value));
        }
        if (targetType.equals(int.class) || targetType.equals(Integer.class)) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        }
        if (targetType.equals(long.class) || targetType.equals(Long.class)) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(String.valueOf(value));
        }
        if (targetType.equals(double.class) || targetType.equals(Double.class)) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            return Double.parseDouble(String.valueOf(value));
        }
        if (targetType.equals(float.class) || targetType.equals(Float.class)) {
            if (value instanceof Number number) {
                return number.floatValue();
            }
            return Float.parseFloat(String.valueOf(value));
        }
        if (targetType.equals(short.class) || targetType.equals(Short.class)) {
            if (value instanceof Number number) {
                return number.shortValue();
            }
            return Short.parseShort(String.valueOf(value));
        }
        if (targetType.equals(byte.class) || targetType.equals(Byte.class)) {
            if (value instanceof Number number) {
                return number.byteValue();
            }
            return Byte.parseByte(String.valueOf(value));
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
                    Match match = findMatch(values, used, type, value -> value instanceof Rule && value != rule);
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
                    Match match = findMatch(values, used, Num.class, Num.class::isInstance);
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
                    return Optional.empty();
                }

                if (type.isEnum()) {
                    Match match = findMatch(values, used, type, value -> type.isInstance(value));
                    if (match == null) {
                        return Optional.empty();
                    }
                    @SuppressWarnings("unchecked")
                    Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) type;
                    arguments.add(Argument.enumValue(name, enumType, (Enum<?>) match.value));
                    continue;
                }

                if (type.equals(String.class)) {
                    Match match = findMatch(values, used, String.class, value -> value instanceof String);
                    if (match == null) {
                        return Optional.empty();
                    }
                    arguments.add(Argument.string(name, match.value));
                    continue;
                }

                if (type.equals(boolean.class) || type.equals(Boolean.class)) {
                    Match match = findMatch(values, used, Boolean.class, value -> value instanceof Boolean);
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

        private static Match findMatch(Map<String, Object> values, Set<String> used, Class<?> type,
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
        LONG_ARRAY, DOUBLE_ARRAY, ENUM_ARRAY
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

        private void serialize(Rule owner, ArgumentContext context) {
            // No longer create metadata - just serialize children and parameters
            // Deserialization will infer constructor signature from these
            switch (kind) {
            case SERIES:
                // Series is passed implicitly, not serialized
                break;
            case RULE:
                Rule rule = (Rule) value;
                ComponentDescriptor ruleDescriptor = RuleSerialization.describe(rule, context.visited);
                context.children.add(applyLabel(ruleDescriptor, label));
                break;
            case INDICATOR:
                Indicator<?> indicator = (Indicator<?>) value;
                ComponentDescriptor indicatorDescriptor = IndicatorSerialization.describe(indicator);
                // Indicators nested in rules need labels for matching during deserialization
                // but we'll serialize them without labels in the JSON
                ComponentDescriptor labeledDescriptor = applyLabel(indicatorDescriptor, label);
                context.children.add(labeledDescriptor);
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
        for (ComponentDescriptor child : descriptor.getChildren()) {
            builder.addChild(child);
        }
        return builder.build();
    }

    private static final class FieldExtractor {

        private static Map<String, Object> extract(Rule rule) {
            Map<String, Object> values = new LinkedHashMap<>();
            Class<?> type = rule.getClass();
            while (type != null && !type.equals(Object.class)) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
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
                    if (shouldIgnore(field.getName(), value)) {
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

        private static boolean shouldIgnore(String name, Object value) {
            if (value instanceof Indicator<?> indicator) {
                return indicator == null;
            }
            if (name.equals(name.toUpperCase())) {
                return true;
            }
            return false;
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
