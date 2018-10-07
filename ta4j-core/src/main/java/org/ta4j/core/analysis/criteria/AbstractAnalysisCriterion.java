package org.ta4j.core.analysis.criteria;

import org.ta4j.core.AnalysisCriterion;

/**
 * An abstract analysis criterion.
 * </p>
 */
public abstract class AbstractAnalysisCriterion implements AnalysisCriterion {

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
