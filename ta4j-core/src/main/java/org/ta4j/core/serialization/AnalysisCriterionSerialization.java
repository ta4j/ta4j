/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.named.NamedAssetKind;
import org.ta4j.core.named.NamedAssetRegistry;

/**
 * Serializes and deserializes {@link AnalysisCriterion} instances with the same
 * descriptor and named-shorthand conventions used by indicators, rules, and
 * strategies.
 *
 * @since 0.22.7
 */
public final class AnalysisCriterionSerialization {

    private static final String CRITERIA_PACKAGE = "org.ta4j.core.criteria";
    private static final String[] CRITERIA_PACKAGES = { CRITERIA_PACKAGE, CRITERIA_PACKAGE + ".pnl",
            CRITERIA_PACKAGE + ".drawdown", CRITERIA_PACKAGE + ".commissions", CRITERIA_PACKAGE + ".helpers",
            CRITERIA_PACKAGE + ".risk" };

    private AnalysisCriterionSerialization() {
    }

    /**
     * Serializes an analysis criterion to canonical descriptor JSON.
     *
     * @param criterion criterion instance
     * @return JSON representation
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
     * @since 0.22.7
     */
    public static ComponentDescriptor describe(AnalysisCriterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        String type = simplifyCriterionType(criterion.getClass());
        return ComponentDescriptor.typeOnly(type);
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
        Constructor<?>[] constructors = criterionType.getDeclaredConstructors();
        List<Constructor<?>> orderedConstructors = new ArrayList<>(constructors.length);
        for (Constructor<?> constructor : constructors) {
            orderedConstructors.add(constructor);
        }
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
            return null;
        }
        if (parameterMetadata.length > index && parameterMetadata[index].isNamePresent()) {
            String parameterName = parameterMetadata[index].getName();
            if (remaining.containsKey(parameterName)) {
                return remaining.remove(parameterName);
            }
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
            return Boolean.parseBoolean(value.toString());
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
            return decimal.intValueExact();
        }
        if (targetType == long.class || targetType == Long.class) {
            return decimal.longValueExact();
        }
        if (targetType == double.class || targetType == Double.class) {
            return decimal.doubleValue();
        }
        if (targetType == float.class || targetType == Float.class) {
            return decimal.floatValue();
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
}
