/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.commissions;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.ReturnRepresentationPolicy;
import org.ta4j.core.criteria.pnl.GrossProfitLossCriterion;
import org.ta4j.core.num.Num;

/**
 * Analysis criterion that measures how much commission reduces gross profit,
 * returned in the configured {@link ReturnRepresentation} format.
 *
 * <p>
 * It expresses the commission impact as the percentage of the gross profit or
 * loss. This helps users understand the real impact of transaction costs on
 * strategy performance.
 *
 * <p>
 * <b>Return Representation:</b> This criterion defaults to
 * {@link ReturnRepresentation#DECIMAL} (ratios are typically expressed as
 * decimals), but you can override it via the constructor. The calculated ratio
 * (which represents the percentage impact) is converted to the configured
 * representation format.
 *
 * <p>
 * <b>Usage Examples:</b>
 *
 * <pre>{@code
 * // Default DECIMAL representation
 * var commissionImpact = new CommissionsImpactPercentageCriterion();
 * // Result: 0.05 means commissions reduce gross profit by 5%
 *
 * // PERCENTAGE representation
 * var commissionImpactPercentage = new CommissionsImpactPercentageCriterion(ReturnRepresentation.PERCENTAGE);
 * // Result: 5.0 means commissions reduce gross profit by 5%
 *
 * // MULTIPLICATIVE representation
 * var commissionImpactMultiplicative = new CommissionsImpactPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE);
 * // Result: 1.05 means commissions reduce gross profit by 5% (1 + 0.05 = 1.05)
 * }</pre>
 *
 * <p>
 * <b>Ratio Format Examples:</b> A ratio of 0.05 (5% commission impact) can be
 * expressed as:
 * <ul>
 * <li><b>DECIMAL</b>: 0.05 (5% impact)
 * <li><b>PERCENTAGE</b>: 5.0 (5% impact)
 * <li><b>MULTIPLICATIVE</b>: 1.05 (1 + 0.05 = 1.05)
 * </ul>
 *
 * @see ReturnRepresentation
 * @see ReturnRepresentationPolicy
 * @since 0.19
 */
public final class CommissionsImpactPercentageCriterion extends AbstractAnalysisCriterion {

    private final ReturnRepresentation returnRepresentation;

    /**
     * Constructor with {@link ReturnRepresentation#DECIMAL} as the default
     * (percentages are typically expressed as decimals).
     * <p>
     * The ratio output will be in DECIMAL format (e.g., 0.05 means 5% commission
     * impact). Use the other constructor to specify a different representation.
     */
    public CommissionsImpactPercentageCriterion() {
        this(ReturnRepresentation.DECIMAL);
    }

    /**
     * Constructor with explicit return representation.
     * <p>
     * Use this constructor to specify how the ratio output should be formatted. The
     * ratio represents the percentage impact of commissions on gross profit. See
     * the class javadoc for examples of how ratios are expressed in different
     * formats.
     *
     * @param returnRepresentation the return representation to use for the output
     *                             ratio (e.g.,
     *                             {@link ReturnRepresentation#DECIMAL},
     *                             {@link ReturnRepresentation#PERCENTAGE},
     *                             {@link ReturnRepresentation#MULTIPLICATIVE})
     */
    public CommissionsImpactPercentageCriterion(ReturnRepresentation returnRepresentation) {
        this.returnRepresentation = returnRepresentation;
    }

    private static final GrossProfitLossCriterion GROSS_PROFIT_LOSS_CRITERION = new GrossProfitLossCriterion();
    private static final CommissionsCriterion COMMISSION_CRITERION = new CommissionsCriterion();

    /**
     * Calculates the commission percentage impact for a single position.
     *
     * @param s the bar series used for number creation
     * @param p the evaluated position
     * @return the percentage of commission relative to gross profit or zero when
     *         there is no gross profit
     */
    @Override
    public Num calculate(BarSeries s, Position p) {
        var numFactory = s.numFactory();
        var zero = numFactory.zero();
        var entry = p.getEntry();
        if (entry == null) {
            return zero;
        }
        var gross = p.getGrossProfit().abs();
        var comm = zero;
        var model = entry.getCostModel();
        var transactionCost = model.calculate(p);
        if (transactionCost != null) {
            comm = transactionCost.abs();
        }
        if (gross.isZero()) {
            return zero;
        }
        // Calculate the ratio as a rate of return (0-based)
        var ratio = comm.dividedBy(gross);
        // Convert the ratio to the configured representation
        return returnRepresentation.toRepresentationFromRateOfReturn(ratio);
    }

    /**
     * Calculates the commission percentage impact for all positions in a trading
     * record.
     *
     * @param s the bar series used for number creation
     * @param r the trading record containing the positions to evaluate
     * @return the percentage of commission relative to the record gross profit or
     *         zero when there is no gross profit, in the configured return
     *         representation format
     */
    @Override
    public Num calculate(BarSeries s, TradingRecord r) {
        var gross = GROSS_PROFIT_LOSS_CRITERION.calculate(s, r).abs();
        var comm = COMMISSION_CRITERION.calculate(s, r).abs();
        var numFactory = s.numFactory();
        if (gross.isZero()) {
            return numFactory.zero();
        }
        // Calculate the ratio as a rate of return (0-based)
        var ratio = comm.dividedBy(gross);
        // Convert the ratio to the configured representation
        return returnRepresentation.toRepresentationFromRateOfReturn(ratio);
    }

    @Override
    public java.util.Optional<ReturnRepresentation> getReturnRepresentation() {
        return java.util.Optional.of(returnRepresentation);
    }

    /**
     * Indicates whether the first percentage is preferable to the second.
     *
     * @param a the first value to compare
     * @param b the second value to compare
     * @return {@code true} when the first value is lower
     * @since 0.19
     */
    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isLessThan(b);
    }

}
