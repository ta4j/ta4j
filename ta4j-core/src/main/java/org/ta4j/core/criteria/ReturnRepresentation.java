/*
 * SPDX-License-Identifier: MIT
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
 * <p>
 * <b>Usage Examples:</b>
 * <ul>
 * <li><b>From total return (1-based)</b>: Use
 * {@link #toRepresentationFromTotalReturn(Num)} when you have a multiplicative
 * total return (e.g., {@code 1.12} for +12% gain). This is commonly used by
 * criteria classes that calculate aggregated returns.
 * <li><b>From rate of return (0-based)</b>: Use
 * {@link #toRepresentationFromRateOfReturn(Num)} when you have an arithmetic
 * rate of return (e.g., {@code 0.12} for +12% gain). This is used by
 * {@link org.ta4j.core.analysis.Returns} when formatting arithmetic returns and
 * by ratio-producing criteria (e.g., {@link VersusEnterAndHoldCriterion},
 * {@link org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion}) when
 * converting ratio outputs.
 * <li><b>From log return</b>: Use {@link #toRepresentationFromLogReturn(Num)}
 * when you have a log return (e.g., {@code ln(1.12) ≈ 0.113} for +12% gain).
 * This is used by risk criteria like {@link ValueAtRiskCriterion} and
 * {@link ExpectedShortfallCriterion}.
 * </ul>
 * <p>
 * <b>Ratio-Producing Criteria:</b> Criteria that produce ratios or percentages
 * (e.g., {@link VersusEnterAndHoldCriterion},
 * {@link org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion},
 * {@link PositionsRatioCriterion}) use
 * {@link #toRepresentationFromRateOfReturn(Num)} to convert ratio outputs. For
 * example, a ratio of 0.5 (50% better) can be expressed as:
 * <ul>
 * <li>DECIMAL: 0.5
 * <li>PERCENTAGE: 50.0
 * <li>MULTIPLICATIVE: 1.5 (1 + 0.5)
 * </ul>
 * See each ratio-producing criterion's javadoc for specific examples.
 *
 * @see Returns
 * @see ReturnRepresentationPolicy
 * @see VersusEnterAndHoldCriterion
 * @since 0.20
 */
public enum ReturnRepresentation {

    /**
     * Multiplicative return (includes base). A {@code 0%} move is represented by
     * {@code 1.0} and {@code +12%} by {@code 1.12}. This format represents the
     * growth factor, where values are multiplied to calculate final amounts.
     */
    MULTIPLICATIVE(true),

    /**
     * Decimal return (excludes base). A {@code 0%} move is represented by
     * {@code 0.0} and {@code +12%} by {@code 0.12}. This format represents the
     * return as a decimal fraction.
     */
    DECIMAL(false),

    /**
     * Percentage value. A {@code 0%} move is represented by {@code 0.0}, a
     * {@code +5%} gain by {@code 5.0}, and a {@code -15%} loss by {@code -15.0}.
     * This representation multiplies the rate of return by 100 for intuitive
     * percentage display.
     */
    PERCENTAGE(false),

    /**
     * Log return (natural logarithm). Returns are calculated as
     * {@code ln(P_i/P_(i-1))} and returned as-is. This representation is useful for
     * statistical analysis and risk calculations (e.g., VaR, Expected Shortfall)
     * where log returns have desirable mathematical properties.
     */
    LOG(false);

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
     * <p>
     * Use this method when you have a total return that includes the base (e.g.,
     * {@code 1.12} for a +12% gain). This is the format used internally by Ta4j
     * criteria for aggregated returns.
     * <p>
     * Example: Converting {@code 1.12} (total return) to PERCENTAGE yields
     * {@code 12.0}.
     *
     * @param totalReturn a 1-based total return (e.g., {@code 1.12} for +12%)
     * @return the represented return in the configured format
     */
    public Num toRepresentationFromTotalReturn(Num totalReturn) {
        if (includesBase) {
            return totalReturn;
        }
        if (this == LOG) {
            return totalReturn.log();
        }
        var rateOfReturn = totalReturn.minus(totalReturn.getNumFactory().one());
        if (this == PERCENTAGE) {
            return rateOfReturn.multipliedBy(totalReturn.getNumFactory().numOf(100));
        }
        return rateOfReturn;
    }

    /**
     * Converts an arithmetic rate of return (0-based) into the configured
     * representation.
     * <p>
     * Use this method when you have an arithmetic rate of return that excludes the
     * base (e.g., {@code 0.12} for a +12% gain). This is the format used by
     * {@link Returns} for raw arithmetic returns.
     * <p>
     * Example: Converting {@code 0.12} (rate of return) to PERCENTAGE yields
     * {@code 12.0}.
     *
     * @param rateOfReturn an arithmetic rate of return (e.g., {@code 0.12} for
     *                     +12%)
     * @return the represented return in the configured format
     */
    public Num toRepresentationFromRateOfReturn(Num rateOfReturn) {
        var one = rateOfReturn.getNumFactory().one();
        if (includesBase) {
            return rateOfReturn.plus(one);
        }
        if (this == LOG) {
            return rateOfReturn.plus(one).log();
        }
        if (this == PERCENTAGE) {
            return rateOfReturn.multipliedBy(rateOfReturn.getNumFactory().numOf(100));
        }
        return rateOfReturn;
    }

    /**
     * Converts a log-return into the configured representation.
     * <p>
     * Use this method when you have a log return calculated as
     * {@code ln(P_i/P_(i-1))}. This is the format used by risk criteria like
     * {@link ValueAtRiskCriterion} and {@link ExpectedShortfallCriterion} for
     * statistical calculations.
     * <p>
     * Example: Converting {@code ln(1.12) ≈ 0.113} (log return) to PERCENTAGE
     * yields approximately {@code 12.0}.
     *
     * @param logReturn the log-return to convert (e.g., {@code ln(1.12)} for +12%)
     * @return the represented return in the configured format
     */
    public Num toRepresentationFromLogReturn(Num logReturn) {
        if (this == LOG) {
            // Log returns are returned as-is
            return logReturn;
        }
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
        if (this == PERCENTAGE) {
            var rateOfReturn = representedReturn.dividedBy(representedReturn.getNumFactory().numOf(100));
            return rateOfReturn.plus(one);
        }
        if (this == LOG) {
            return representedReturn.exp();
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
        if (this == PERCENTAGE) {
            return representedReturn.dividedBy(representedReturn.getNumFactory().numOf(100));
        }
        if (this == LOG) {
            var one = representedReturn.getNumFactory().one();
            return representedReturn.exp().minus(one);
        }
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
        return addBase ? MULTIPLICATIVE : DECIMAL;
    }

    /**
     * Parses a representation name in a case-insensitive way.
     * <p>
     * Accepts various formats including spaces, dashes, underscores, and mixed
     * case. Examples: "multiplicative", "MULTIPLICATIVE", "decimal", "Decimal",
     * "percentage", "Percentage"
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
