/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import org.ta4j.core.num.Num;

/**
 * Captures the ratio between two consecutive Elliott swings.
 *
 * <p>
 * Produced by {@link ElliottRatioIndicator} and used by confluence and
 * validation logic to classify retracements versus extensions.
 *
 * @param value measured ratio (absolute amplitude of the latest swing divided
 *              by the previous swing amplitude)
 * @param type  classification of the ratio (retracement or extension)
 * @since 0.22.0
 */
public record ElliottRatio(Num value, RatioType type) {

    /**
     * @return {@code true} when the ratio has an actionable type and a valid
     *         numeric value.
     * @since 0.22.0
     */
    public boolean isValid() {
        return type != null && type != RatioType.NONE && Num.isValid(value);
    }

    /**
     * Type of ratio relationship between consecutive swings.
     *
     * @since 0.22.0
     */
    public enum RatioType {
        /**
         * Indicates that price reversed direction relative to the prior swing
         * (retracing a portion of the previous move).
         */
        RETRACEMENT,
        /**
         * Indicates that price continued in the same direction (projecting an extension
         * beyond the prior swing).
         */
        EXTENSION,
        /**
         * Returned when an actionable ratio could not be computed.
         */
        NONE
    }
}
