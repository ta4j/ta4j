/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.named.NamedAssetKind;
import org.ta4j.core.named.NamedAssetRegistry;

/**
 * Serializes and deserializes {@link AnalysisCriterion} instances with the same
 * descriptor and named-shorthand conventions used by indicators, rules, and
 * strategies.
 * <p>
 * Descriptor serialization is lossless-or-fail: constructor state is included
 * when it can be mapped safely, and unsupported stateful criteria are rejected
 * rather than silently emitted as default instances.
 *
 * @since 0.22.7
 */
public final class AnalysisCriterionSerialization {

    private static final String CRITERIA_PACKAGE = "org.ta4j.core.criteria";
    private static final String[] CRITERIA_PACKAGES = { CRITERIA_PACKAGE, CRITERIA_PACKAGE + ".pnl",
            CRITERIA_PACKAGE + ".drawdown", CRITERIA_PACKAGE + ".commissions", CRITERIA_PACKAGE + ".helpers",
            CRITERIA_PACKAGE + ".risk" };
    private static final Object MISSING_PARAMETER = new Object();

    private AnalysisCriterionSerialization() {
    }

    /**
     * Serializes an analysis criterion to canonical descriptor JSON.
     *
     * @param criterion criterion instance
     * @return JSON representation
     * @throws IllegalArgumentException if the criterion has constructor state that
     *                                  cannot be represented safely
     * @since 0.22.7
     */
    public static String toJson(AnalysisCriterion criterion) {
        return ComponentSerialization.toJson(describe(criterion));
    }

    /**
     * Converts an analysis criterion into a descriptor.
     *
     * @param criterion criterion instance
     * @return descriptor
     * @throws IllegalArgumentException if the criterion has constructor state that
     *                                  cannot be represented safely
     * @since 0.22.7
     */
    public static ComponentDescriptor describe(AnalysisCriterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        String type = simplifyCriterionType(criterion.getClass());
        Map<String, Object> parameters = describeParameters(criterion);
        ComponentDescriptor.Builder builder = ComponentDescriptor.builder().withType(type);
        if (!parameters.isEmpty()) {
            builder.withParameters(parameters);
        }
        return builder.build();
    }

    /**
     * Rebuilds an analysis criterion from canonical descriptor JSON.
     *
     * @param json JSON payload
     * @return reconstructed criterion
     * @since 0.22.7
     */
    public static AnalysisCriterion fromJson(String json) {
        ComponentDescriptor descriptor = ComponentSerialization.parse(json);
        return fromDescriptor(descriptor);
    }

    /**
     * Rebuilds an analysis criterion from a descriptor.
     *
     * @param descriptor descriptor
     * @return reconstructed criterion
     * @since 0.22.7
     */
    public static AnalysisCriterion fromDescriptor(ComponentDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        Class<? extends AnalysisCriterion> criterionType = resolveCriterionClass(descriptor.getType());
        return instantiate(criterionType, descriptor);
    }

    /**
     * Renders an analysis criterion as a compact named shorthand expression using
     * the default registry.
     *
     * @param criterion criterion instance
     * @return compact expression
     * @since 0.22.7
     */
    public static String toExpression(AnalysisCriterion criterion) {
        return toExpression(criterion, NamedAssetRegistry.defaultRegistry());
    }

    /**
     * Renders an analysis criterion as a compact named shorthand expression using
     * the supplied registry.
     *
     * @param criterion criterion instance
     * @param registry  named asset registry
     * @return compact expression
     * @since 0.22.7
     */
    public static String toExpression(AnalysisCriterion criterion, NamedAssetRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ComponentDescriptor descriptor = describe(criterion);
        return registry.toExpression(NamedAssetKind.ANALYSIS_CRITERION, descriptor)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No named analysis criterion shorthand registered for descriptor: " + descriptor));
    }

    /**
     * Rebuilds an analysis criterion from a compact named shorthand expression
     * using the default registry.
     *
     * @param expression shorthand expression
     * @return reconstructed criterion
     * @since 0.22.7
     */
    public static AnalysisCriterion fromExpression(String expression) {
        return fromExpression(expression, NamedAssetRegistry.defaultRegistry());
    }

    /**
     * Rebuilds an analysis criterion from a compact named shorthand expression
     * using the supplied registry.
     *
     * @param expression shorthand expression
     * @param registry   named asset registry
     * @return reconstructed criterion
     * @since 0.22.7
     */
    public static AnalysisCriterion fromExpression(String expression, NamedAssetRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ComponentDescriptor descriptor = registry.toDescriptor(NamedAssetKind.ANALYSIS_CRITERION, expression);
        return fromDescriptor(descriptor);
    }

    private static String simplifyCriterionType(Class<?> type) {
        String packageName = type.getPackageName();
        if (packageName != null && packageName.startsWith(CRITERIA_PACKAGE)) {
            return type.getSimpleName();
        }
        return type.getName();
    }

    private static Map<String, Object> describeParameters(AnalysisCriterion criterion) {
        Map<String, Object> currentState = serializableState(criterion);
        Optional<AnalysisCriterion> defaultCriterion = instantiateDefault(criterion.getClass());
        if (defaultCriterion.isPresent() && currentState.equals(serializableState(defaultCriterion.get()))) {
            return Map.of();
        }
        List<Constructor<?>> constructors = constructorsByParameterCount(criterion.getClass());
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() == 0) {
                continue;
            }
            Map<String, Object> currentValues = constructorParameterValues(criterion, constructor);
            if (currentValues == null) {
                continue;
            }
            if (rebuildsSerializableState(criterion.getClass(), currentValues, currentState)) {
                return currentValues;
            }
        }
        throw new IllegalArgumentException(
                "Analysis criterion state cannot be serialized safely: " + criterion.getClass().getName());
    }

    private static boolean rebuildsSerializableState(Class<? extends AnalysisCriterion> criterionType,
            Map<String, Object> parameters, Map<String, Object> expectedState) {
        try {
            ComponentDescriptor descriptor = ComponentDescriptor.builder()
                    .withType(criterionType.getName())
                    .withParameters(parameters)
                    .build();
            AnalysisCriterion rebuilt = instantiate(criterionType, descriptor);
            return expectedState.equals(serializableState(rebuilt));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Optional<AnalysisCriterion> instantiateDefault(Class<?> criterionType) {
        try {
            Constructor<?> constructor = criterionType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return Optional.of((AnalysisCriterion) constructor.newInstance());
        } catch (NoSuchMethodException ex) {
            return Optional.empty();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException(
                    "Failed to construct default analysis criterion: " + criterionType.getName(), ex);
        }
    }

    private static List<Constructor<?>> constructorsByParameterCount(Class<?> criterionType) {
        Constructor<?>[] constructors = criterionType.getDeclaredConstructors();
        List<Constructor<?>> orderedConstructors = new ArrayList<>(constructors.length);
        for (Constructor<?> constructor : constructors) {
            orderedConstructors.add(constructor);
        }
        orderedConstructors.sort(Comparator.comparingInt(Constructor::getParameterCount));
        return orderedConstructors;
    }

    private static Map<String, Object> constructorParameterValues(AnalysisCriterion criterion,
            Constructor<?> constructor) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        java.lang.reflect.Parameter[] parameterMetadata = constructor.getParameters();
        for (int index = 0; index < constructor.getParameterCount(); index++) {
            if (parameterMetadata.length <= index || !parameterMetadata[index].isNamePresent()) {
                return null;
            }
            String parameterName = parameterMetadata[index].getName();
            Optional<Object> value = fieldValue(criterion, parameterName, new IdentityHashMap<>(), 0);
            if (value.isEmpty()) {
                value = uniqueFieldValueByType(criterion, constructor.getParameterTypes()[index]);
            }
            if (value.isEmpty()) {
                return null;
            }
            values.put(parameterName, descriptorParameterValue(value.get()));
        }
        return values;
    }

    private static Optional<Object> uniqueFieldValueByType(Object source, Class<?> parameterType) {
        List<Object> candidates = new ArrayList<>();
        collectFieldValuesByType(source, parameterType, new IdentityHashMap<>(), 0, candidates);
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }
        return Optional.empty();
    }

    private static void collectFieldValuesByType(Object source, Class<?> parameterType,
            IdentityHashMap<Object, Boolean> visited, int depth, List<Object> candidates) {
        if (source == null || visited.containsKey(source) || depth > 2) {
            return;
        }
        visited.put(source, Boolean.TRUE);
        for (Class<?> type = source.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                Object value = readField(field, source);
                if (value != null && isDescriptorParameterValue(value)
                        && isCompatibleParameterValue(parameterType, value)) {
                    candidates.add(value);
                }
                if (value != null && shouldSearchNestedValue(value)) {
                    collectFieldValuesByType(value, parameterType, visited, depth + 1, candidates);
                }
            }
        }
    }

    private static Optional<Object> fieldValue(Object source, String name, IdentityHashMap<Object, Boolean> visited,
            int depth) {
        if (source == null || visited.containsKey(source) || depth > 2) {
            return Optional.empty();
        }
        visited.put(source, Boolean.TRUE);
        for (Class<?> type = source.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                Object value = readField(field, source);
                if (field.getName().equals(name) && value != null && isDescriptorParameterValue(value)) {
                    return Optional.of(value);
                }
                if (value != null && shouldSearchNestedValue(value)) {
                    Optional<Object> nestedValue = fieldValue(value, name, visited, depth + 1);
                    if (nestedValue.isPresent()) {
                        return nestedValue;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Map<String, Object> serializableState(Object source) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        collectSerializableState(source, "", new IdentityHashMap<>(), 0, values);
        return values;
    }

    private static void collectSerializableState(Object source, String prefix, IdentityHashMap<Object, Boolean> visited,
            int depth, Map<String, Object> values) {
        if (source == null || visited.containsKey(source) || depth > 2) {
            return;
        }
        visited.put(source, Boolean.TRUE);
        for (Class<?> type = source.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                Object value = readField(field, source);
                String key = prefix + field.getName();
                if (value != null && isDescriptorParameterValue(value)) {
                    values.put(key, descriptorParameterValue(value));
                } else if (value != null && shouldSearchNestedValue(value)) {
                    collectSerializableState(value, key + ".", visited, depth + 1, values);
                }
            }
        }
    }

    private static Object readField(Field field, Object source) {
        try {
            field.setAccessible(true);
            return field.get(source);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Failed to read analysis criterion field: " + field.getName(), ex);
        }
    }

    private static boolean shouldSearchNestedValue(Object value) {
        Package valuePackage = value.getClass().getPackage();
        return valuePackage != null && valuePackage.getName().startsWith("org.ta4j.core")
                && !isDescriptorParameterValue(value);
    }

    private static boolean isDescriptorParameterValue(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Enum<?> || value instanceof ZoneId || value instanceof BigDecimal;
    }

    private static boolean isCompatibleParameterValue(Class<?> parameterType, Object value) {
        Class<?> boxedType = boxedType(parameterType);
        if (boxedType.isInstance(value)) {
            return true;
        }
        if (boxedType == String.class || Number.class.isAssignableFrom(boxedType)) {
            return value instanceof String || value instanceof Number || value instanceof BigDecimal;
        }
        if (boxedType.isEnum()) {
            return value instanceof Enum<?> enumValue && boxedType.isInstance(enumValue);
        }
        return boxedType == ZoneId.class && value instanceof ZoneId;
    }

    private static Class<?> boxedType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static Object descriptorParameterValue(Object value) {
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof ZoneId zoneId) {
            return zoneId.getId();
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends AnalysisCriterion> resolveCriterionClass(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Analysis criterion descriptor missing type");
        }
        try {
            Class<?> clazz = Class.forName(type);
            if (AnalysisCriterion.class.isAssignableFrom(clazz)) {
                return (Class<? extends AnalysisCriterion>) clazz;
            }
        } catch (ClassNotFoundException ex) {
            // Try standard criterion packages below.
        }
        for (String packageName : CRITERIA_PACKAGES) {
            try {
                Class<?> clazz = Class.forName(packageName + "." + type);
                if (AnalysisCriterion.class.isAssignableFrom(clazz)) {
                    return (Class<? extends AnalysisCriterion>) clazz;
                }
            } catch (ClassNotFoundException ex) {
                // Continue through candidate packages.
            }
        }
        throw new IllegalArgumentException("Unknown analysis criterion type: " + type);
    }

    private static AnalysisCriterion instantiate(Class<? extends AnalysisCriterion> criterionType,
            ComponentDescriptor descriptor) {
        List<Constructor<?>> orderedConstructors = constructorsByParameterCount(criterionType);
        orderedConstructors.sort((left, right) -> Integer.compare(right.getParameterCount(), left.getParameterCount()));
        for (Constructor<?> constructor : orderedConstructors) {
            Object[] arguments = matchConstructor(constructor, descriptor.getParameters());
            if (arguments == null) {
                continue;
            }
            try {
                constructor.setAccessible(true);
                Object instance = constructor.newInstance(arguments);
                return (AnalysisCriterion) instance;
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                throw new IllegalStateException("Failed to construct analysis criterion: " + criterionType.getName(),
                        ex);
            }
        }
        throw new IllegalArgumentException(
                "No compatible constructor found for analysis criterion: " + criterionType.getName());
    }

    private static Object[] matchConstructor(Constructor<?> constructor, Map<String, Object> parameters) {
        Map<String, Object> remaining = parameters == null ? Map.of() : new LinkedHashMap<>(parameters);
        if (constructor.getParameterCount() != remaining.size()) {
            return null;
        }
        Object[] arguments = new Object[constructor.getParameterCount()];
        java.lang.reflect.Parameter[] parameterMetadata = constructor.getParameters();
        for (int index = 0; index < constructor.getParameterCount(); index++) {
            Class<?> parameterType = constructor.getParameterTypes()[index];
            Object rawValue = pollParameterValue(remaining, parameterMetadata, index);
            if (rawValue == MISSING_PARAMETER) {
                return null;
            }
            Object converted = convertValue(rawValue, parameterType);
            if (converted == null && rawValue != null) {
                return null;
            }
            arguments[index] = converted;
        }
        return remaining.isEmpty() ? arguments : null;
    }

    private static Object pollParameterValue(Map<String, Object> remaining,
            java.lang.reflect.Parameter[] parameterMetadata, int index) {
        if (remaining.isEmpty()) {
            return MISSING_PARAMETER;
        }
        if (parameterMetadata.length > index && parameterMetadata[index].isNamePresent()) {
            String parameterName = parameterMetadata[index].getName();
            if (remaining.containsKey(parameterName)) {
                return remaining.remove(parameterName);
            }
            return MISSING_PARAMETER;
        }
        String fallbackName = "arg" + index;
        if (remaining.containsKey(fallbackName)) {
            return remaining.remove(fallbackName);
        }
        String firstKey = remaining.keySet().iterator().next();
        return remaining.remove(firstKey);
    }

    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return value.toString();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            String text = value.toString();
            if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                return Boolean.parseBoolean(text);
            }
            return null;
        }
        if (targetType.isEnum()) {
            return enumValue(targetType, value);
        }
        if (targetType == ZoneId.class) {
            return ZoneId.of(value.toString());
        }
        BigDecimal decimal;
        try {
            decimal = new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return exactInt(decimal);
        }
        if (targetType == long.class || targetType == Long.class) {
            return exactLong(decimal);
        }
        if (targetType == double.class || targetType == Double.class) {
            double converted = decimal.doubleValue();
            return Double.isFinite(converted) ? converted : null;
        }
        if (targetType == float.class || targetType == Float.class) {
            float converted = decimal.floatValue();
            return Float.isFinite(converted) ? converted : null;
        }
        if (targetType == BigDecimal.class) {
            return decimal;
        }
        if (Number.class.isAssignableFrom(targetType)) {
            return decimal;
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object enumValue(Class<?> targetType, Object value) {
        return Enum.valueOf((Class<? extends Enum>) targetType, value.toString());
    }

    private static Integer exactInt(BigDecimal decimal) {
        try {
            return decimal.intValueExact();
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static Long exactLong(BigDecimal decimal) {
        try {
            return decimal.longValueExact();
        } catch (ArithmeticException ex) {
            return null;
        }
    }
}
