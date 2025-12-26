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
package org.ta4j.core.indicators.elliott;

import org.ta4j.core.num.Num;

/**
 * Captures the ratio between two consecutive Elliott swings.
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
