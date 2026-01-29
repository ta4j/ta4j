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

import java.util.Optional;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.Returns;
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
 * RoMaD = net return (without base) / {@link MaximumDrawdownCriterion maximum drawdown}
 * </pre>
 *
 * <p>
 * <b>Return Representation:</b> This criterion defaults to
 * {@link ReturnRepresentation#DECIMAL} (ratios are typically expressed as
 * decimals), but you can override it via the constructor. The calculated ratio
 * (which represents how much return is achieved per unit of drawdown) is
 * converted to the configured representation format.
 *
 * <p>
 * <b>Usage Examples:</b>
 *
 * <pre>{@code
 * // Default DECIMAL representation
 * var romad = new ReturnOverMaxDrawdownCriterion();
 * // Result: 2.0 means return is 2x the maximum drawdown
 *
 * // PERCENTAGE representation
 * var romadPercentage = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
 * // Result: 200.0 means return is 200% of the maximum drawdown
 *
 * // MULTIPLICATIVE representation
 * var romadMultiplicative = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.MULTIPLICATIVE);
 * // Result: 3.0 means return is 200% better than drawdown (1 + 2.0 = 3.0)
 * }</pre>
 *
 * <p>
 * <b>Ratio Format Examples:</b> A ratio of 2.0 (return is 2x the drawdown) can
 * be expressed as:
 * <ul>
 * <li><b>DECIMAL</b>: 2.0 (return is 2x the drawdown)
 * <li><b>PERCENTAGE</b>: 200.0 (return is 200% of the drawdown)
 * <li><b>MULTIPLICATIVE</b>: 3.0 (1 + 2.0 = 3.0, meaning 200% better)
 * </ul>
 *
 * @see ReturnRepresentation
 * @see ReturnRepresentationPolicy
 */
public class ReturnOverMaxDrawdownCriterion extends AbstractAnalysisCriterion {

    private final AnalysisCriterion netReturnCriterion;
    private final AnalysisCriterion maxDrawdownCriterion = new MaximumDrawdownCriterion();
    private final ReturnRepresentation returnRepresentation;

    /**
     * Constructor with {@link ReturnRepresentation#DECIMAL} as the default (ratios
     * are typically expressed as decimals).
     * <p>
     * The ratio output will be in DECIMAL format (e.g., 2.0 means return is 2x the
     * drawdown). Use the other constructor to specify a different representation.
     */
    public ReturnOverMaxDrawdownCriterion() {
        this(ReturnRepresentation.DECIMAL);
    }

    /**
     * Constructor with explicit return representation.
     * <p>
     * Use this constructor to specify how the ratio output should be formatted. The
     * ratio represents how much return is achieved per unit of drawdown. See the
     * class javadoc for examples of how ratios are expressed in different formats.
     *
     * @param returnRepresentation the return representation to use for the output
     *                             ratio (e.g.,
     *                             {@link ReturnRepresentation#DECIMAL},
     *                             {@link ReturnRepresentation#PERCENTAGE},
     *                             {@link ReturnRepresentation#MULTIPLICATIVE})
     */
    public ReturnOverMaxDrawdownCriterion(ReturnRepresentation returnRepresentation) {
        this.returnRepresentation = returnRepresentation;
        // requires "net return without base" (rate of return). The final ratio will be
        // converted to the desired representation.
        this.netReturnCriterion = new NetReturnCriterion(ReturnRepresentation.DECIMAL);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var numFactory = series.numFactory();
        if (position.isOpened()) {
            return numFactory.zero();
        }
        var maxDrawdown = maxDrawdownCriterion.calculate(series, position);
        // Get the net return in DECIMAL (0-based) for the formula calculation
        var netReturn = netReturnCriterion.calculate(series, position);
        if (maxDrawdown.isZero()) {
            // If no drawdown, convert the net return to the desired representation
            return returnRepresentation.toRepresentationFromRateOfReturn(netReturn);
        }
        // Calculate ratio: net return (0-based) / drawdown
        // Both are in DECIMAL format, so the ratio is also in DECIMAL
        var rawRatio = netReturn.dividedBy(maxDrawdown);
        // Convert the ratio to the desired representation
        if (returnRepresentation == ReturnRepresentation.DECIMAL) {
            return rawRatio;
        }
        if (returnRepresentation == ReturnRepresentation.MULTIPLICATIVE) {
            // For MULTIPLICATIVE, return 1 + rawRatio for positive ratios, or rawRatio
            // as-is for negative ratios (since 1 + negative doesn't make intuitive sense)
            var one = numFactory.one();
            var zero = numFactory.zero();
            if (rawRatio.isGreaterThanOrEqual(zero)) {
                return rawRatio.plus(one);
            } else {
                return rawRatio;
            }
        }
        // For PERCENTAGE, multiply the ratio by 100
        return rawRatio.multipliedBy(numFactory.numOf(100));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        if (tradingRecord.getPositions().isEmpty()) {
            return numFactory.zero(); // penalise no-trade strategies
        }
        var maxDrawdown = maxDrawdownCriterion.calculate(series, tradingRecord);
        // Get the net return in DECIMAL (0-based) for the formula calculation
        var netReturn = netReturnCriterion.calculate(series, tradingRecord);
        if (maxDrawdown.isZero()) {
            // If no drawdown, convert the net return to the desired representation
            return returnRepresentation.toRepresentationFromRateOfReturn(netReturn);
        }
        // Calculate ratio: net return (0-based) / drawdown
        // Both are in DECIMAL format, so the ratio is also in DECIMAL
        var rawRatio = netReturn.dividedBy(maxDrawdown);
        // Convert the ratio to the desired representation
        if (returnRepresentation == ReturnRepresentation.DECIMAL) {
            return rawRatio;
        }
        if (returnRepresentation == ReturnRepresentation.MULTIPLICATIVE) {
            // For MULTIPLICATIVE, return 1 + rawRatio for positive ratios, or rawRatio
            // as-is for negative ratios (since 1 + negative doesn't make intuitive sense)
            var one = numFactory.one();
            var zero = numFactory.zero();
            if (rawRatio.isGreaterThanOrEqual(zero)) {
                return rawRatio.plus(one);
            } else {
                return rawRatio;
            }
        }
        // For PERCENTAGE, multiply the ratio by 100
        return rawRatio.multipliedBy(numFactory.numOf(100));
    }

    private Num calculateNetReturn(BarSeries series, TradingRecord tradingRecord) {
        var returns = new Returns(series, tradingRecord, ReturnRepresentation.DECIMAL);
        var numFactory = series.numFactory();
        var one = numFactory.one();
        var totalReturn = one;
        for (var rawReturn : returns.getRawValues()) {
            if (Num.isNaNOrNull(rawReturn)) {
                continue;
            }
            totalReturn = totalReturn.multipliedBy(rawReturn.plus(one));
        }
        return totalReturn.minus(one);
    }

    @Override
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.of(returnRepresentation);
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
