/*
 * SPDX-License-Identifier: MIT
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
