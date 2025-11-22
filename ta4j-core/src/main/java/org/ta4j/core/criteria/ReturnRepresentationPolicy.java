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

import org.ta4j.core.analysis.Returns;

import java.util.Objects;
import java.util.Optional;

/**
 * Holds the global default {@link ReturnRepresentation} used across Ta4j
 * criteria.
 * <p>
 * The default can be changed at runtime to unify outputs across criteria and
 * backtests:
 *
 * <pre>
 * ReturnRepresentationPolicy.setDefaultRepresentation(ReturnRepresentation.DECIMAL);
 * </pre>
 * <p>
 * A JVM-wide override is also supported via the system property
 * {@value #SYSTEM_PROPERTY}. Allowed values match the names of
 * {@link ReturnRepresentation}.
 * <p>
 * <b>Usage Notes:</b>
 * <ul>
 * <li><b>Return-based criteria</b> (e.g.,
 * {@link org.ta4j.core.criteria.pnl.NetReturnCriterion}) use this policy as
 * their default representation.
 * <li><b>Ratio-producing criteria</b> (e.g.,
 * {@link VersusEnterAndHoldCriterion},
 * {@link org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion})
 * typically default to {@link ReturnRepresentation#DECIMAL} regardless of this
 * policy, as ratios are conventionally expressed as decimals. However, you can
 * override the representation per-criterion via constructors.
 * </ul>
 * <p>
 * This policy is used by {@link Returns} and various criteria classes when no
 * explicit representation is provided.
 *
 * @see ReturnRepresentation
 * @see Returns
 * @see VersusEnterAndHoldCriterion
 */
public final class ReturnRepresentationPolicy {

    /** System property used to select the default representation. */
    public static final String SYSTEM_PROPERTY = "ta4j.returns.representation";

    private static volatile ReturnRepresentation defaultRepresentation = Optional
            .ofNullable(System.getProperty(SYSTEM_PROPERTY))
            .map(ReturnRepresentation::parse)
            .orElse(ReturnRepresentation.MULTIPLICATIVE);

    private ReturnRepresentationPolicy() {
    }

    /**
     * @return the currently configured default representation
     */
    public static ReturnRepresentation getDefaultRepresentation() {
        return defaultRepresentation;
    }

    /**
     * Sets the default return representation used across Ta4j criteria.
     *
     * @param representation the return representation to set as default, or null to
     *                       reset to MULTIPLICATIVE
     */
    public static void setDefaultRepresentation(ReturnRepresentation representation) {
        defaultRepresentation = Objects.requireNonNullElse(representation, ReturnRepresentation.MULTIPLICATIVE);
    }
}
