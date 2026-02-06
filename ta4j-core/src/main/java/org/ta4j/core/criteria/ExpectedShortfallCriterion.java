/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.Returns;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.ReturnRepresentationPolicy;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Expected Shortfall criterion, honoring the configured return representation.
 *
 * <p>
 * Measures the expected shortfall of the strategy log-return time-series.
 *
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Expected_shortfall">https://en.wikipedia.org/wiki/Expected_shortfall</a>
 *
 */
public class ExpectedShortfallCriterion extends AbstractAnalysisCriterion {

    /** Confidence level as absolute value (e.g. 0.95). */
    private final double confidence;

    private final ReturnRepresentation returnRepresentation;

    /**
     * Constructor.
     *
     * @param confidence the confidence level
     */
    public ExpectedShortfallCriterion(double confidence) {
        this(confidence, ReturnRepresentationPolicy.getDefaultRepresentation());
    }

    /**
     * Constructor.
     *
     * @param confidence           the confidence level
     * @param returnRepresentation the representation used for the result
     */
    public ExpectedShortfallCriterion(double confidence, ReturnRepresentation returnRepresentation) {
        this.confidence = confidence;
        this.returnRepresentation = returnRepresentation;
    }

    @Override
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.of(returnRepresentation);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null || position.getExit() == null) {
            return getNeutralValue(series.numFactory());
        }
        Returns returns = new Returns(series, position, ReturnRepresentation.LOG);
        return calculateES(returns, confidence, returnRepresentation);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Returns returns = new Returns(series, tradingRecord, ReturnRepresentation.LOG);
        return calculateES(returns, confidence, returnRepresentation);
    }

    /**
     * Calculates the Expected Shortfall on the return series.
     *
     * @param returns    the corresponding returns
     * @param confidence the confidence level
     * @return the relative Expected Shortfall
     */
    private Num calculateES(Returns returns, double confidence, ReturnRepresentation representation) {
        // select non-NaN returns (use raw values for statistical calculations)
        List<Num> returnRates = returns.getRawValues().subList(1, returns.getSize() + 1);
        Num zero = returns.getBarSeries().numFactory().zero();
        if (returnRates.isEmpty()) {
            return getNeutralValue(returns.getBarSeries().numFactory());
        }
        Num expectedShortfall = zero;
        // F(x_var) >= alpha (=1-confidence)
        int nInBody = (int) (returns.getSize() * confidence);
        int nInTail = returns.getSize() - nInBody;

        // calculate average tail loss
        Collections.sort(returnRates);
        List<Num> tailEvents = returnRates.subList(0, nInTail);
        Num sum = zero;
        for (int i = 0; i < nInTail; i++) {
            sum = sum.plus(tailEvents.get(i));
        }
        expectedShortfall = sum.dividedBy(returns.getBarSeries().numFactory().numOf(nInTail));

        // ES is non-positive
        if (expectedShortfall.isGreaterThan(zero)) {
            expectedShortfall = zero;
        }

        // Format the final result according to the representation
        return representation.toRepresentationFromLogReturn(expectedShortfall);
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

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
