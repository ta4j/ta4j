/*
 * SPDX-License-Identifier: MIT
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
 * the configured {@link ReturnRepresentation} format. This ratio represents how
 * much profit is achieved per unit of loss, which is a key metric for
 * evaluating strategy quality.
 *
 * <p>
 * <b>Return Representation:</b> This criterion defaults to
 * {@link ReturnRepresentation#DECIMAL} (ratios are typically expressed as
 * decimals), but you can override it via the constructor. The calculated ratio
 * is converted to the configured representation format.
 *
 * <p>
 * <b>Usage Examples:</b>
 *
 * <pre>{@code
 * // Default DECIMAL representation (used by GrossProfitLossRatioCriterion, NetProfitLossRatioCriterion)
 * var profitLossRatio = new GrossProfitLossRatioCriterion();
 * // Result: 2.0 means average profit is 2x the average loss
 *
 * // PERCENTAGE representation
 * var profitLossRatioPercentage = new GrossProfitLossRatioCriterion(ReturnRepresentation.PERCENTAGE);
 * // Result: 100.0 means average profit is 100% better than average loss (i.e., 2x)
 *
 * // MULTIPLICATIVE representation
 * var profitLossRatioMultiplicative = new GrossProfitLossRatioCriterion(ReturnRepresentation.MULTIPLICATIVE);
 * // Result: 3.0 means average profit is 200% better than average loss (1 + 2.0 = 3.0)
 * }</pre>
 *
 * <p>
 * <b>Ratio Format Examples:</b> A ratio of 2.0 (profit is 2x the loss) can be
 * expressed as:
 * <ul>
 * <li><b>DECIMAL</b>: 2.0 (profit is 2x the loss)
 * <li><b>PERCENTAGE</b>: 100.0 (profit is 100% better than loss, i.e., (2.0 -
 * 1) * 100)
 * <li><b>MULTIPLICATIVE</b>: 3.0 (1 + 2.0 = 3.0, meaning profit is 200% better
 * than loss)
 * </ul>
 *
 * <p>
 * <b>Special Cases:</b>
 * <ul>
 * <li>If there are no losses (averageLoss = 0), the ratio is set to 1.0
 * (neutral, meaning profit equals loss)
 * <li>If there are no profits (averageProfit = 0), the ratio is 0.0
 * </ul>
 *
 * @see ReturnRepresentation
 * @see ReturnRepresentationPolicy
 * @see org.ta4j.core.criteria.pnl.GrossProfitLossRatioCriterion
 * @see org.ta4j.core.criteria.pnl.NetProfitLossRatioCriterion
 */
public abstract class AbstractProfitLossRatioCriterion extends AbstractAnalysisCriterion {

    private final AnalysisCriterion averageProfitCriterion;
    private final AnalysisCriterion averageLossCriterion;
    private final ReturnRepresentation returnRepresentation;

    /**
     * Constructor with {@link ReturnRepresentation#DECIMAL} as the default (ratios
     * are typically expressed as decimals).
     * <p>
     * The ratio output will be in DECIMAL format (e.g., 2.0 means profit is 2x the
     * loss). Use the other constructor to specify a different representation.
     *
     * @param averageProfitCriterion the criterion for average profit
     * @param averageLossCriterion   the criterion for average loss
     */
    protected AbstractProfitLossRatioCriterion(AnalysisCriterion averageProfitCriterion,
            AnalysisCriterion averageLossCriterion) {
        this(averageProfitCriterion, averageLossCriterion, ReturnRepresentation.DECIMAL);
    }

    /**
     * Constructor with explicit return representation.
     * <p>
     * Use this constructor to specify how the ratio output should be formatted. The
     * ratio represents how much profit is achieved per unit of loss. See the class
     * javadoc for examples of how ratios are expressed in different formats.
     *
     * @param averageProfitCriterion the criterion for average profit
     * @param averageLossCriterion   the criterion for average loss
     * @param returnRepresentation   the return representation to use for the output
     *                               ratio (e.g.,
     *                               {@link ReturnRepresentation#DECIMAL},
     *                               {@link ReturnRepresentation#PERCENTAGE},
     *                               {@link ReturnRepresentation#MULTIPLICATIVE})
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
        // - MULTIPLICATIVE: 3.0 (1 + 2.0 = 3.0, meaning profit is 200% better than
        // loss)
        if (returnRepresentation == ReturnRepresentation.DECIMAL) {
            return rawRatio;
        }
        if (returnRepresentation == ReturnRepresentation.MULTIPLICATIVE) {
            // For MULTIPLICATIVE, return 1 + rawRatio to represent the multiplicative
            // factor
            // A ratio of 2.0 becomes 3.0 (1 + 2.0), meaning profit is 200% better
            var one = numFactory.one();
            return rawRatio.plus(one);
        }
        // For PERCENTAGE, convert (ratio - 1) as a rate of return
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
