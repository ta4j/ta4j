/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Represents a price channel with upper/lower boundaries and an optional
 * median. Implementations may return {@code NaN} for the median when it is not
 * defined.
 *
 * <p>
 * Common implementations include:
 * <ul>
 * <li>{@link org.ta4j.core.indicators.elliott.ElliottChannel} - Elliott Wave
 * price channels for wave validation</li>
 * <li>Bollinger Bands - Statistical channels based on standard deviation</li>
 * <li>Keltner Channels - Volatility-based channels using ATR</li>
 * </ul>
 *
 * <p>
 * The interface provides default implementations for common operations:
 * <ul>
 * <li>{@link #isValid()} - Checks if channel boundaries are valid numbers</li>
 * <li>{@link #width()} - Calculates the distance between upper and lower
 * boundaries</li>
 * <li>{@link #contains(Num, Num)} - Tests if a price is within the channel,
 * optionally with tolerance</li>
 * </ul>
 *
 * @since 0.22.0
 */
public interface PriceChannel {

    /**
     * Identifies a channel boundary role with a standard label.
     *
     * @since 0.22.0
     */
    enum Boundary {
        /** Upper channel boundary (resistance). */
        UPPER("upper"),
        /** Lower channel boundary (support). */
        LOWER("lower"),
        /** Median channel boundary (midline). */
        MEDIAN("median");

        private final String label;

        Boundary(String label) {
            this.label = label;
        }

        /**
         * Returns the canonical label for this boundary.
         *
         * @return the label (e.g. "upper")
         */
        public String label() {
            return label;
        }
    }

    /**
     * Returns the upper channel boundary.
     *
     * @return the upper boundary
     */
    Num upper();

    /**
     * Returns the lower channel boundary.
     *
     * @return the lower boundary
     */
    Num lower();

    /**
     * Returns the median channel boundary, if defined.
     *
     * @return the median boundary (or NaN when not available)
     */
    Num median();

    /**
     * Returns {@code true} when both channel boundaries are valid numbers.
     *
     * @return {@code true} if upper and lower values are valid
     * @since 0.22.0
     */
    default boolean isValid() {
        return Num.isValid(upper()) && Num.isValid(lower());
    }

    /**
     * Returns the channel width (distance between upper and lower boundaries).
     *
     * @return {@code upper - lower}, or NaN if the channel is invalid
     * @since 0.22.0
     */
    default Num width() {
        if (!isValid()) {
            return NaN.NaN;
        }
        return upper().minus(lower());
    }

    /**
     * Checks whether a price is contained within the channel, optionally extended
     * by a symmetric tolerance.
     *
     * @param price     price to test
     * @param tolerance optional symmetric tolerance around the boundaries
     * @return {@code true} if the price lies between {@code lower - tolerance} and
     *         {@code upper + tolerance}
     * @since 0.22.0
     */
    default boolean contains(final Num price, final Num tolerance) {
        if (!isValid() || Num.isNaNOrNull(price)) {
            return false;
        }
        final NumFactory factory = lower().getNumFactory();
        final Num effectiveTolerance = tolerance == null ? factory.zero() : tolerance;
        if (Num.isNaNOrNull(effectiveTolerance)) {
            return false;
        }
        final Num min = lower().minus(effectiveTolerance);
        final Num max = upper().plus(effectiveTolerance);
        return price.isGreaterThanOrEqual(min) && price.isLessThanOrEqual(max);
    }
}
