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

import java.util.Locale;
import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Normalizes how percentage/return based criteria are exposed to users.
 * <p>
 * Ta4j performs internal calculations using multiplicative total returns (a
 * value of {@code 1.12} means a +12% gain, {@code 0.85} means a -15% loss).
 * {@code ReturnRepresentation} defines how that internal value is returned to
 * callers. The global default can be overridden via
 * {@link ReturnRepresentationPolicy} or by passing a representation to the
 * relevant criterion constructor. All conversion helpers expect the provided
 * {@code one} value to be produced by the same
 * {@link org.ta4j.core.num.NumFactory} used by the surrounding
 * {@link org.ta4j.core.BarSeries} to avoid mixing numeric implementations.
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
     * @param one         the numeric {@code 1}
     * @return the represented return
     */
    public Num toRepresentationFromTotalReturn(Num totalReturn, Num one) {
        if (includesBase) {
            return totalReturn;
        }
        return totalReturn.minus(one);
    }

    /**
     * Converts an arithmetic rate of return (0-based) into the configured
     * representation.
     *
     * @param rate an arithmetic rate of return
     * @param one  the numeric {@code 1}
     * @return the represented return
     */
    public Num toRepresentationFromRate(Num rate, Num one) {
        if (includesBase) {
            return rate.plus(one);
        }
        return rate;
    }

    /**
     * Converts a log-return into the configured representation.
     *
     * @param logReturn the log-return to convert
     * @param one       the numeric {@code 1}
     * @return the represented return
     */
    public Num toRepresentationFromLogReturn(Num logReturn, Num one) {
        var totalReturn = toTotalReturnFromLogReturn(logReturn, one);
        return toRepresentationFromTotalReturn(totalReturn, one);
    }

    /**
     * Converts a represented value into a multiplicative total return.
     *
     * @param representedReturn the return expressed using this representation
     * @param one               the numeric {@code 1}
     * @return a 1-based total return
     */
    public Num toTotalReturn(Num representedReturn, Num one) {
        if (includesBase) {
            return representedReturn;
        }
        return representedReturn.plus(one);
    }

    /**
     * Converts a represented value into a 0-based rate of return.
     *
     * @param representedReturn the return expressed using this representation
     * @param one               the numeric {@code 1}
     * @return a 0-based rate of return
     */
    public Num toRateOfReturn(Num representedReturn, Num one) {
        return toTotalReturn(representedReturn, one).minus(one);
    }

    /**
     * Converts a log return into a multiplicative total return.
     *
     * @param logReturn the log-return to convert
     * @param one       the numeric {@code 1}
     * @return a 1-based total return
     */
    public Num toTotalReturnFromLogReturn(Num logReturn, Num one) {
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
     *
     * @param name the textual representation name
     * @return the matching representation
     */
    public static ReturnRepresentation parse(String name) {
        Objects.requireNonNull(name, "name");
        String normalized = name.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return ReturnRepresentation.valueOf(normalized);
    }
}
