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
package org.ta4j.core.criteria;

import org.ta4j.core.num.Num;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Normalizes how percentage/return based criteria are exposed to users.
 * <p>
 * Ta4j performs internal calculations using multiplicative total returns (a
 * value of {@code 1.12} means a +12% gain, {@code 0.85} means a -15% loss).
 * {@code ReturnRepresentation} defines how that internal value is returned to
 * callers. The global default can be overridden via
 * {@link ReturnRepresentationPolicy} or by passing a representation to the
 * relevant criterion constructor. All conversion helpers automatically use the
 * {@link org.ta4j.core.num.NumFactory} from the provided {@code Num} parameter
 * to ensure consistent numeric implementations.
 *
 * @since 0.20
 */
public enum ReturnRepresentation {

    /**
     * 1-based total return. A {@code 0%} move is represented by {@code 1.0} and
     * {@code +12%} by {@code 1.12}.
     */
    TOTAL_RETURN(true),

    /**
     * 0-based rate of return. A {@code 0%} move is represented by {@code 0.0} and
     * {@code +12%} by {@code 0.12}.
     */
    RATE_OF_RETURN(false);

    private static final Logger log = LoggerFactory.getLogger(ReturnRepresentation.class);

    private final boolean includesBase;

    ReturnRepresentation(boolean includesBase) {
        this.includesBase = includesBase;
    }

    /**
     * @return whether the representation includes the base (1.0) value
     */
    public boolean includesBase() {
        return includesBase;
    }

    /**
     * Converts a multiplicative total return into the configured representation.
     *
     * @param totalReturn a 1-based total return
     * @return the represented return
     */
    public Num toRepresentationFromTotalReturn(Num totalReturn) {
        if (includesBase) {
            return totalReturn;
        }
        return totalReturn.minus(totalReturn.getNumFactory().one());
    }

    /**
     * Converts an arithmetic rate of return (0-based) into the configured
     * representation.
     *
     * @param rateOfReturn an arithmetic rate of return
     * @return the represented return
     */
    public Num toRepresentationFromRateOfReturn(Num rateOfReturn) {
        var one = rateOfReturn.getNumFactory().one();
        if (includesBase) {
            return rateOfReturn.plus(one);
        }
        return rateOfReturn;
    }

    /**
     * Converts a log-return into the configured representation.
     *
     * @param logReturn the log-return to convert
     * @return the represented return
     */
    public Num toRepresentationFromLogReturn(Num logReturn) {
        var totalReturn = toTotalReturnFromLogReturn(logReturn);
        return toRepresentationFromTotalReturn(totalReturn);
    }

    /**
     * Converts a represented value into a multiplicative total return.
     *
     * @param representedReturn the return expressed using this representation
     * @return a 1-based total return
     */
    public Num toTotalReturn(Num representedReturn) {
        var one = representedReturn.getNumFactory().one();
        if (includesBase) {
            return representedReturn;
        }
        return representedReturn.plus(one);
    }

    /**
     * Converts a represented value into a 0-based rate of return.
     *
     * @param representedReturn the return expressed using this representation
     * @return a 0-based rate of return
     */
    public Num toRateOfReturn(Num representedReturn) {
        var one = representedReturn.getNumFactory().one();
        return toTotalReturn(representedReturn).minus(one);
    }

    /**
     * Converts a log return into a multiplicative total return.
     *
     * @param logReturn the log-return to convert
     * @return a 1-based total return
     */
    public Num toTotalReturnFromLogReturn(Num logReturn) {
        return logReturn.exp();
    }

    /**
     * Maps the legacy {@code addBase} flag to a {@link ReturnRepresentation}.
     *
     * @param addBase whether the base should be added
     * @return the matching representation
     */
    public static ReturnRepresentation fromAddBase(boolean addBase) {
        return addBase ? TOTAL_RETURN : RATE_OF_RETURN;
    }

    /**
     * Parses a representation name in a case-insensitive way.
     * <p>
     * Accepts various formats including spaces, dashes, underscores, and mixed
     * case. Examples: "total return", "TOTAL_RETURN", "rate-of-return", "Rate Of
     * Return"
     * <p>
     * If parsing fails, an error is logged and {@code null} is returned.
     *
     * @param name the textual representation name
     * @return the matching representation, or {@code null} if parsing fails
     */
    public static ReturnRepresentation parse(String name) {
        if (name == null) {
            log.warn("Cannot parse null representation name. Valid values are: {}", Arrays.toString(values()));
            return null;
        }

        // Fast path: try exact match first (case-sensitive)
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ignored) {
            // Continue to normalization
        }

        // Normalize the input: trim, uppercase, replace non-alphanumeric with
        // underscore
        String normalized = normalizeEnumName(name);

        if (normalized.isEmpty()) {
            log.warn("Cannot parse empty or invalid representation name: \"{}\". Valid values are: {}", name,
                    Arrays.toString(values()));
            return null;
        }

        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            log.warn("Cannot parse representation name: \"{}\" (normalized: \"{}\"). Valid values are: {}", name,
                    normalized, Arrays.toString(values()));
            return null;
        }
    }

    /**
     * Normalizes a string to match enum naming conventions. Converts to uppercase,
     * replaces non-alphanumeric characters with underscores, and removes
     * leading/trailing underscores.
     *
     * @param name the name to normalize
     * @return the normalized name
     */
    private static String normalizeEnumName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        // Use StringBuilder for better performance than regex
        StringBuilder builder = new StringBuilder(name.length());
        boolean lastWasUnderscore = false;
        boolean hasValidChar = false;

        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);

            if (Character.isLetterOrDigit(ch)) {
                builder.append(Character.toUpperCase(ch));
                lastWasUnderscore = false;
                hasValidChar = true;
            } else if (!lastWasUnderscore && hasValidChar) {
                // Only add underscore if we've seen a valid character and last wasn't
                // underscore
                // This handles multiple consecutive separators
                builder.append('_');
                lastWasUnderscore = true;
            }
            // Skip other characters (spaces, dashes, etc.)
        }

        // Remove trailing underscore if present
        int length = builder.length();
        if (length > 0 && builder.charAt(length - 1) == '_') {
            builder.setLength(length - 1);
        }

        return builder.toString();
    }
}
