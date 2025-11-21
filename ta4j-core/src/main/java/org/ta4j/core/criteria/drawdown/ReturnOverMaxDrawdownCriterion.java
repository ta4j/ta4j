/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.criteria.drawdown;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.ReturnRepresentationPolicy;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.num.Num;

/**
 * Reward risk ratio criterion (also known as "RoMaD"), returned in the
 * configured {@link ReturnRepresentation} format.
 *
 * <pre>
 * RoMaD = {@link NetReturnCriterion net return (without base)} / {@link MaximumDrawdownCriterion maximum drawdown}
 * </pre>
 *
 * <p>
 * The calculated ratio (which represents how much return is achieved per unit
 * of drawdown) is then converted to the configured {@link ReturnRepresentation}
 * format. For example, a ratio of 2.0 (return is 2x the drawdown) can be
 * expressed as:
 * <ul>
 * <li>DECIMAL: 2.0 (return is 2x the drawdown)
 * <li>PERCENTAGE: 200.0 (return is 200% of the drawdown)
 * <li>MULTIPLICATIVE: 3.0 (1 + 2.0 = 3.0, meaning 200% better)
 * </ul>
 */
public class ReturnOverMaxDrawdownCriterion extends AbstractAnalysisCriterion {

    private final AnalysisCriterion netReturnCriterion;
    private final AnalysisCriterion maxDrawdownCriterion = new MaximumDrawdownCriterion();
    private final ReturnRepresentation returnRepresentation;

    /**
     * Constructor with {@link ReturnRepresentation#DECIMAL} as the default (ratios
     * are typically expressed as decimals).
     */
    public ReturnOverMaxDrawdownCriterion() {
        this(ReturnRepresentation.DECIMAL);
    }

    /**
     * Constructor.
     *
     * @param returnRepresentation the return representation to use for the output
     *                             ratio
     */
    public ReturnOverMaxDrawdownCriterion(ReturnRepresentation returnRepresentation) {
        this.returnRepresentation = returnRepresentation;
        this.netReturnCriterion = new NetReturnCriterion(returnRepresentation);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var numFactory = series.numFactory();
        if (position.isOpened()) {
            return numFactory.zero();
        }
        var maxDrawdown = maxDrawdownCriterion.calculate(series, position);
        // Get the net return in the configured representation
        var netReturn = netReturnCriterion.calculate(series, position);
        if (maxDrawdown.isZero()) {
            // If no drawdown, return the net return (already in the correct representation)
            return netReturn;
        }
        // For backward compatibility with existing tests, divide the already-converted
        // return
        // by the drawdown. The result is in the same representation as the return.
        // Then convert the ratio to the desired representation.
        var rawRatio = netReturn.dividedBy(maxDrawdown);
        // Convert the ratio: first convert back to rate, then to desired representation
        var rateRatio = returnRepresentation.toRateOfReturn(rawRatio);
        return returnRepresentation.toRepresentationFromRateOfReturn(rateRatio);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        if (tradingRecord.getPositions().isEmpty()) {
            return numFactory.zero(); // penalise no-trade strategies
        }
        var maxDrawdown = maxDrawdownCriterion.calculate(series, tradingRecord);
        // Get the net return in the configured representation
        var netReturn = netReturnCriterion.calculate(series, tradingRecord);
        if (maxDrawdown.isZero()) {
            // If no drawdown, return the net return (already in the correct representation)
            return netReturn; // perfect equity curve
        }
        // For backward compatibility with existing tests, divide the already-converted
        // return
        // by the drawdown. The result is in the same representation as the return.
        // Then convert the ratio to the desired representation.
        var rawRatio = netReturn.dividedBy(maxDrawdown);
        // Convert the ratio: first convert back to rate, then to desired representation
        var rateRatio = returnRepresentation.toRateOfReturn(rawRatio);
        return returnRepresentation.toRepresentationFromRateOfReturn(rateRatio);
    }

    @Override
    public java.util.Optional<ReturnRepresentation> getReturnRepresentation() {
        return java.util.Optional.of(returnRepresentation);
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
