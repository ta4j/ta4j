/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ta4j.core.analysis.Returns;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Shared tail-selection support for {@link ValueAtRiskCriterion} and
 * {@link ExpectedShortfallCriterion}.
 * <p>
 * Both risk criteria operate on the same view of a return series: the raw
 * returns excluding the initial placeholder value at index 0, sorted ascending,
 * with the tail size derived from the confidence level.
 */
final class RiskTailSupport {

    private RiskTailSupport() {
    }

    /**
     * Returns the raw return rates of the given series, excluding the initial
     * placeholder value at index 0, sorted ascending.
     *
     * @param returns the return series
     * @return the sorted raw return rates
     */
    static List<Num> sortedRates(Returns returns) {
        List<Num> returnRates = new ArrayList<>(returns.getRawValues().subList(1, returns.getSize() + 1));
        Collections.sort(returnRates);
        return returnRates;
    }

    /**
     * Returns the number of returns in the distribution tail for the given
     * confidence level (F(x_var) &gt;= alpha, where alpha = 1 - confidence).
     *
     * @param size       the size of the return series
     * @param confidence the confidence level
     * @return the number of tail returns
     */
    static int nInTail(int size, double confidence) {
        int nInBody = (int) (size * confidence);
        return size - nInBody;
    }

    /**
     * Returns the neutral value (no return) in the target representation format.
     *
     * @param numFactory           the number factory
     * @param returnRepresentation the return representation
     * @return the neutral value in the target representation
     */
    static Num neutralValue(NumFactory numFactory, ReturnRepresentation returnRepresentation) {
        if (returnRepresentation == ReturnRepresentation.MULTIPLICATIVE) {
            return numFactory.one();
        }
        // DECIMAL, PERCENTAGE, and LOG all use 0.0 as neutral
        return numFactory.zero();
    }
}
