/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.Optional;

import org.ta4j.core.AnalysisCriterion;

/**
 * An abstract analysis criterion.
 */
public abstract class AbstractAnalysisCriterion implements AnalysisCriterion {

    /**
     * Returns the return representation used by this criterion, if applicable.
     * <p>
     * Criteria that use {@link ReturnRepresentation} should override this method to
     * return their representation. Criteria that don't use return representations
     * should not override this method (it defaults to empty).
     *
     * @return the return representation, or empty if this criterion doesn't use
     *         return representations
     */
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        String[] tokens = getClass().getSimpleName().split("(?=\\p{Lu})", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length - 1; i++) {
            sb.append(tokens[i]).append(' ');
        }
        return sb.toString().trim();
    }
}
