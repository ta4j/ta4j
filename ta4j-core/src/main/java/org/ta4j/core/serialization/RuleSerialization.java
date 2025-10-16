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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
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
 * their inputs into derived indicators without keeping the original
 * constructor arguments may not be fully supported.
 *
 * @since 0.19
 */
public final class RuleSerialization {

    private static final String ARGUMENTS_KEY = "__args";

    private RuleSerialization() {
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

        ComponentDescriptor.Builder builder = ComponentDescriptor.builder()
                .withType(rule.getClass().getName());

        Map<String, Object> parameters = new LinkedHashMap<>();
        List<ComponentDescriptor> children = new ArrayList<>();

        List<Map<String, Object>> metadata = new ArrayList<>();
        ArgumentContext context = new ArgumentContext(parameters, children, metadata, visited);
        for (Argument argument : match.arguments) {
            argument.serialize(rule, context);
        }

        parameters.put(ARGUMENTS_KEY, metadata);
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

        Object argsMeta = descriptor.getParameters().get(ARGUMENTS_KEY);
        if (!(argsMeta instanceof List<?> rawList)) {
            throw new IllegalArgumentException("Rule descriptor missing argument metadata: " + descriptor);
        }

        List<Map<String, Object>> metadata = new ArrayList<>(rawList.size());
        for (Object element : rawList) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Invalid argument metadata entry: " + element);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    entry.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            metadata.add(entry);
        }

        ReconstructionContext context = new ReconstructionContext(series, descriptor);
        Object[] arguments = new Object[metadata.size()];
        Class<?>[] parameterTypes = new Class<?>[metadata.size()];

        for (int i = 0; i < metadata.size(); i++) {
            Map<String, Object> entry = metadata.get(i);
            ArgumentKind kind = ArgumentKind.valueOf(String.valueOf(entry.get("kind")));
            String name = (String) entry.get("name");
            String targetType = (String) entry.get("target");
            parameterTypes[i] = context.resolveClass(targetType);
            arguments[i] = context.resolveArgument(kind, name, entry, parameterTypes[i]);
        }

        try {
            Constructor<? extends Rule> constructor = locateConstructor(ruleType, parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to construct rule: " + ruleType.getName(), ex);
        }
    }

    private static Constructor<? extends Rule> locateConstructor(Class<? extends Rule> type, Class<?>[] parameterTypes) {
        try {
            return type.getDeclaredConstructor(parameterTypes);
        } catch (NoSuchMethodException ex) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                Class<?>[] declared = constructor.getParameterTypes();
                if (declared.length != parameterTypes.length) {
                    continue;
                }
                boolean matches = true;
                for (int i = 0; i < declared.length; i++) {
                    if (!declared[i].isAssignableFrom(parameterTypes[i])) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    @SuppressWarnings("unchecked")
                    Constructor<? extends Rule> result = (Constructor<? extends Rule>) constructor;
                    return result;
                }
            }
            throw new IllegalStateException("No compatible constructor found for rule type: " + type.getName());
        }
    }

    private static final class ArgumentContext {

        private final Map<String, Object> parameters;
        private final List<ComponentDescriptor> children;
        private final List<Map<String, Object>> metadata;
        private final IdentityHashMap<Rule, ComponentDescriptor> visited;

        private ArgumentContext(Map<String, Object> parameters, List<ComponentDescriptor> children,
                List<Map<String, Object>> metadata, IdentityHashMap<Rule, ComponentDescriptor> visited) {
            this.parameters = parameters;
            this.children = children;
            this.metadata = metadata;
            this.visited = visited;
        }
    }

    private static final class ReconstructionContext {

        private final BarSeries series;
        private final ComponentDescriptor descriptor;
        private final Map<String, ComponentDescriptor> childrenByLabel;

        private ReconstructionContext(BarSeries series, ComponentDescriptor descriptor) {
            this.series = series;
            this.descriptor = descriptor;
            if (descriptor.getChildren().isEmpty()) {
                this.childrenByLabel = Collections.emptyMap();
            } else {
                Map<String, ComponentDescriptor> map = new LinkedHashMap<>();
                for (ComponentDescriptor child : descriptor.getChildren()) {
                    if (child != null && child.getLabel() != null) {
                        map.put(child.getLabel(), child);
                    }
                }
                this.childrenByLabel = map;
            }
        }

        private Object resolveArgument(ArgumentKind kind, String name, Map<String, Object> metadata, Class<?> targetType) {
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
            case BOOLEAN:
                return convertBoolean(descriptor.getParameters().get(name));
            case STRING:
                Object value = descriptor.getParameters().get(name);
                return value == null ? null : String.valueOf(value);
            case ENUM:
                String enumClassName = String.valueOf(metadata.get("enumType"));
                return resolveEnum(name, enumClassName, targetType);
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

        private Object resolveEnum(String name, String enumClassName, Class<?> targetType) {
            Object raw = descriptor.getParameters().get(name);
            if (raw == null) {
                throw new IllegalArgumentException("Missing enum parameter: " + name);
            }
            try {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                Class<? extends Enum> enumType = (Class<? extends Enum>) Class.forName(enumClassName);
                String label = String.valueOf(raw);
                return Enum.valueOf(enumType, label);
            } catch (ClassNotFoundException ex) {
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
                    yield Class.forName(typeName);
                } catch (ClassNotFoundException ex) {
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

        private static Optional<List<Argument>> match(Rule rule, Constructor<?> constructor, Map<String, Object> values) {
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
    }

    private record Match(String key, Object value) {
        private String label() {
            int lastDot = key.lastIndexOf('.');
            return lastDot >= 0 ? key.substring(lastDot + 1) : key;
        }
    }

    private enum ArgumentKind {
        SERIES, RULE, INDICATOR, NUM, NUMBER, INT, LONG, DOUBLE, BOOLEAN, STRING, ENUM
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

        private void serialize(Rule owner, ArgumentContext context) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("kind", kind.name());
            metadata.put("name", name);
            metadata.put("target", targetType.getName());

            switch (kind) {
            case SERIES:
                break;
            case RULE:
                Rule rule = (Rule) value;
                ComponentDescriptor ruleDescriptor = RuleSerialization.describe(rule, context.visited);
                context.children.add(applyLabel(ruleDescriptor, label));
                metadata.put("label", label);
                break;
            case INDICATOR:
                Indicator<?> indicator = (Indicator<?>) value;
                ComponentDescriptor indicatorDescriptor = IndicatorSerialization.describe(indicator);
                context.children.add(applyLabel(indicatorDescriptor, label));
                metadata.put("label", label);
                break;
            case NUM:
                context.parameters.put(name, value == null ? null : String.valueOf(value));
                break;
            case ENUM:
                Enum<?> enumValue = (Enum<?>) value;
                context.parameters.put(name, enumValue == null ? null : enumValue.name());
                metadata.put("enumType", targetType.getName());
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
            default:
                throw new IllegalStateException("Unsupported argument kind: " + kind);
            }

            context.metadata.add(metadata);
        }

        private static Object serializeNumber(Object value) {
            if (value instanceof Num num) {
                return String.valueOf(num);
            }
            return value;
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
            for (Class<?> current = indicatorType; current != null && !current.equals(Object.class); current = current
                    .getSuperclass()) {
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

