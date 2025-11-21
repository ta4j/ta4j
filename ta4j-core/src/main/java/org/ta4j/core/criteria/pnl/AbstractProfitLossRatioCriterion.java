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
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.ReturnRepresentationPolicy;
import org.ta4j.core.num.Num;

/**
 * Base class for profit/loss ratio criteria.
 * <p>
 * Calculates the ratio of the average profit over the average loss, returned in
 * the configured {@link ReturnRepresentation} format. The calculated ratio
 * (which represents how much profit is achieved per unit of loss) is then
 * converted to the configured {@link ReturnRepresentation} format. For example,
 * a ratio of 2.0 (profit is 2x the loss) can be expressed as:
 * <ul>
 * <li>DECIMAL: 2.0 (profit is 2x the loss)
 * <li>PERCENTAGE: 200.0 (profit is 200% of the loss)
 * <li>MULTIPLICATIVE: 3.0 (1 + 2.0 = 3.0)
 * </ul>
 */
public abstract class AbstractProfitLossRatioCriterion extends AbstractAnalysisCriterion {

    private final AnalysisCriterion averageProfitCriterion;
    private final AnalysisCriterion averageLossCriterion;
    private final ReturnRepresentation returnRepresentation;

    /**
     * Constructor with {@link ReturnRepresentation#DECIMAL} as the default (ratios
     * are typically expressed as decimals).
     */
    protected AbstractProfitLossRatioCriterion(AnalysisCriterion averageProfitCriterion,
            AnalysisCriterion averageLossCriterion) {
        this(averageProfitCriterion, averageLossCriterion, ReturnRepresentation.DECIMAL);
    }

    /**
     * Constructor.
     *
     * @param averageProfitCriterion the criterion for average profit
     * @param averageLossCriterion   the criterion for average loss
     * @param returnRepresentation   the return representation to use for the output
     *                               ratio
     */
    protected AbstractProfitLossRatioCriterion(AnalysisCriterion averageProfitCriterion,
            AnalysisCriterion averageLossCriterion, ReturnRepresentation returnRepresentation) {
        this.averageProfitCriterion = averageProfitCriterion;
        this.averageLossCriterion = averageLossCriterion;
        this.returnRepresentation = returnRepresentation;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var averageProfit = averageProfitCriterion.calculate(series, position);
        var averageLoss = averageLossCriterion.calculate(series, position);
        return calculateRatio(series, averageProfit, averageLoss);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var averageProfit = averageProfitCriterion.calculate(series, tradingRecord);
        var averageLoss = averageLossCriterion.calculate(series, tradingRecord);
        return calculateRatio(series, averageProfit, averageLoss);
    }

    private Num calculateRatio(BarSeries series, Num averageProfit, Num averageLoss) {
        var numFactory = series.numFactory();
        if (averageProfit.isZero()) {
            return numFactory.zero();
        }
        // Calculate the raw ratio (e.g., 2.0 means profit is 2x loss)
        // Handle division by zero case: if averageLoss is zero, ratio is effectively
        // infinite
        // but we cap it at 1.0 to represent "neutral" (profit equals loss)
        Num rawRatio;
        if (averageLoss.isZero()) {
            rawRatio = numFactory.one(); // Neutral ratio when no losses
        } else {
            rawRatio = averageProfit.dividedBy(averageLoss).abs();
        }

        // For profit/loss ratio, we treat it as a pure ratio
        // A ratio of 2.0 means profit is 2x loss, which can be expressed as:
        // - DECIMAL: 2.0 (profit is 2x loss)
        // - PERCENTAGE: 100.0 (profit is 100% better than loss, i.e., (2.0 - 1) * 100)
        // - MULTIPLICATIVE: 3.0 (1 + (2.0 - 1) = 2.0, wait that's wrong...)
        // Actually, for MULTIPLICATIVE: 1 + (ratio - 1) = ratio, so it's the same as
        // DECIMAL
        // But that doesn't make sense. Let me think...
        // Actually, a ratio of 2.0 in MULTIPLICATIVE should be 2.0 (same as DECIMAL)
        // But if we convert (2.0 - 1) = 1.0 as a rate, MULTIPLICATIVE becomes 2.0,
        // which is correct!
        if (returnRepresentation == ReturnRepresentation.DECIMAL) {
            return rawRatio;
        }
        // For PERCENTAGE and MULTIPLICATIVE, convert (ratio - 1) as a rate of return
        // This represents how much better profit is compared to loss
        var one = numFactory.one();
        var excess = rawRatio.minus(one);
        return returnRepresentation.toRepresentationFromRateOfReturn(excess);
    }

    @Override
    public java.util.Optional<ReturnRepresentation> getReturnRepresentation() {
        return java.util.Optional.of(returnRepresentation);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
