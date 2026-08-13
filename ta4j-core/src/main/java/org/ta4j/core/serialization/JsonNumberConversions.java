/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import java.math.BigDecimal;
import java.util.regex.Pattern;

final class JsonNumberConversions {

    private static final Pattern JSON_NUMBER_LITERAL = Pattern
            .compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    private JsonNumberConversions() {
    }

    static BigDecimal parseFiniteJsonNumber(String value, String location) {
        String trimmed = value == null ? "" : value.trim();
        if (!isJsonNumberLiteral(trimmed)) {
            throw new IllegalArgumentException("Invalid numeric argument at " + location + ": " + trimmed);
        }
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numeric argument at " + location + ": " + trimmed, ex);
        }
    }

    static Object convertJsonNumber(Object value, Class<?> targetType) {
        BigDecimal decimal = jsonNumberOrNull(value);
        if (decimal == null) {
            return null;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return exactInt(decimal);
        }
        if (targetType == long.class || targetType == Long.class) {
            return exactLong(decimal);
        }
        if (targetType == short.class || targetType == Short.class) {
            return exactShort(decimal);
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return exactByte(decimal);
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
        if (targetType == Object.class) {
            return decimal;
        }
        if (targetType == Number.class || Number.class.isAssignableFrom(targetType)) {
            if (targetType.isInstance(value) && isFinitePrimitiveNumber(value)) {
                return value;
            }
            return decimal;
        }
        return null;
    }

    private static BigDecimal jsonNumberOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double doubleValue && !Double.isFinite(doubleValue)) {
            throw new IllegalArgumentException("Invalid finite JSON number: " + value);
        }
        if (value instanceof Float floatValue && !Float.isFinite(floatValue)) {
            throw new IllegalArgumentException("Invalid finite JSON number: " + value);
        }
        String text = value.toString().trim();
        if (!isJsonNumberLiteral(text)) {
            if (looksLikeNonFiniteNumber(text) || value instanceof Number) {
                throw new IllegalArgumentException("Invalid finite JSON number: " + text);
            }
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid finite JSON number: " + text, ex);
        }
    }

    private static boolean isJsonNumberLiteral(String value) {
        return value != null && JSON_NUMBER_LITERAL.matcher(value).matches();
    }

    private static boolean looksLikeNonFiniteNumber(String value) {
        return "NaN".equals(value) || "Infinity".equals(value) || "-Infinity".equals(value)
                || "+Infinity".equals(value);
    }

    private static boolean isFinitePrimitiveNumber(Object value) {
        if (value instanceof Double doubleValue) {
            return Double.isFinite(doubleValue);
        }
        if (value instanceof Float floatValue) {
            return Float.isFinite(floatValue);
        }
        return true;
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

    private static Short exactShort(BigDecimal decimal) {
        try {
            return decimal.shortValueExact();
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static Byte exactByte(BigDecimal decimal) {
        try {
            return decimal.byteValueExact();
        } catch (ArithmeticException ex) {
            return null;
        }
    }
}
