/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.Returns;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Value at Risk criterion, honoring the configured return representation.
 *
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Value_at_risk">https://en.wikipedia.org/wiki/Value_at_risk</a>
 */
public class ValueAtRiskCriterion extends AbstractAnalysisCriterion {

    /** Confidence level as absolute value (e.g. 0.95). */
    private final Double confidence;

    private final ReturnRepresentation returnRepresentation;

    /**
     * Constructor.
     *
     * @param confidence the confidence level
     */
    public ValueAtRiskCriterion(Double confidence) {
        this(confidence, ReturnRepresentationPolicy.getDefaultRepresentation());
    }

    /**
     * Constructor.
     *
     * @param confidence           the confidence level
     * @param returnRepresentation the representation used for the result
     */
    public ValueAtRiskCriterion(Double confidence, ReturnRepresentation returnRepresentation) {
        this.confidence = confidence;
        this.returnRepresentation = returnRepresentation;
    }

    @Override
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.of(returnRepresentation);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position == null || !position.isClosed()) {
            return getNeutralValue(series.numFactory());
        }
        Returns returns = new Returns(series, position, ReturnRepresentation.LOG);
        return calculateVaR(returns, confidence, returnRepresentation);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Returns returns = new Returns(series, tradingRecord, ReturnRepresentation.LOG);
        return calculateVaR(returns, confidence, returnRepresentation);
    }

    /**
     * Calculates the VaR on the return series.
     *
     * @param returns    the corresponding returns
     * @param confidence the confidence level
     * @return the relative Value at Risk
     */
    private Num calculateVaR(Returns returns, double confidence, ReturnRepresentation representation) {
        Num zero = returns.getBarSeries().numFactory().zero();
        // select non-NaN returns (use raw values for statistical calculations)
        List<Num> returnRates = returns.getRawValues().subList(1, returns.getSize() + 1);
        if (returnRates.isEmpty()) {
            return getNeutralValue(returns.getBarSeries().numFactory());
        }

        Num valueAtRisk = zero;
        // F(x_var) >= alpha (=1-confidence)
        int nInBody = (int) (returns.getSize() * confidence);
        int nInTail = returns.getSize() - nInBody;

        // The series is not empty, nInTail > 0
        Collections.sort(returnRates);
        valueAtRisk = returnRates.get(nInTail - 1);

        // VaR is non-positive
        if (valueAtRisk.isGreaterThan(zero)) {
            valueAtRisk = zero;
        }
        // Format the final result according to the representation
        return representation.toRepresentationFromLogReturn(valueAtRisk);
    }

    /**
     * Returns the neutral value (no return) in the target representation format.
     *
     * @param numFactory the number factory
     * @return the neutral value in the target representation
     */
    private Num getNeutralValue(NumFactory numFactory) {
        if (returnRepresentation == ReturnRepresentation.MULTIPLICATIVE) {
            return numFactory.one();
        }
        // DECIMAL, PERCENTAGE, and LOG all use 0.0 as neutral
        return numFactory.zero();
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        // because it represents a loss, VaR is non-positive
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
