/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

@FunctionalInterface
public interface CriterionFactory {

    /**
     * Applies parameters to a CriterionFactory and returns the AnalysisCriterion.
     *
     * @param params criteria parameters
     * @return AnalysisCriterion with the parameters applied
     */
    AnalysisCriterion getCriterion(Object... params);

}
