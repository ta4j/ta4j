/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.AnalysisCriterion;

/**
 * Marker for criteria whose values are actual strategy or position returns.
 *
 * <p>
 * Implementations represent a return using {@link ReturnRepresentation}.
 * Criteria that expose {@link ReturnRepresentation} only to format ratios, hit
 * rates, exposure percentages, risk measures, or other non-return quantities
 * should not implement this interface.
 *
 * <p>
 * {@link ActiveReturnCriterion} uses this marker, together with
 * {@link AbstractAnalysisCriterion#getReturnRepresentation()}, to reject
 * represented non-return ratios at construction time.
 *
 * @since 0.22.7
 */
public interface ReturnCriterion extends AnalysisCriterion {
}
