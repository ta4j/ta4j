/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.rules;

import org.ta4j.core.analysis.elliott.topology.*;

/**
 * Structured evidence state of one relationship rule evaluation.
 *
 * <p>
 * Every state is explicit: missing inputs yield {@code UNAVAILABLE}, not a
 * pass; future-dependent checks yield {@code PENDING}; inapplicable shapes
 * yield {@code NOT_APPLICABLE}. Failures stay failures.
 */
public enum EvidenceState {
    PASS, FAIL, PENDING, UNAVAILABLE, NOT_APPLICABLE
}
