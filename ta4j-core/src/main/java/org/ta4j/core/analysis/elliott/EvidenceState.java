/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

/**
 * Structured evidence state of one relationship rule evaluation.
 *
 * <p>
 * Every state is explicit: missing inputs yield {@code UNAVAILABLE}, not a
 * pass; future-dependent checks yield {@code PENDING}; inapplicable shapes
 * yield {@code NOT_APPLICABLE}. Failures stay failures.
 */
enum EvidenceState {
    PASS, FAIL, PENDING, UNAVAILABLE, NOT_APPLICABLE
}
