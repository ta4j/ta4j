/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.drawdown;

import java.util.Objects;
import java.util.Optional;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.criteria.AbstractEquityCurveSettingsCriterion;
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
 * ReturnOverMaxDrawdownCriterion romad = new ReturnOverMaxDrawdownCriterion();
 * // Result: 2.0 means return is 2x the maximum drawdown
 *
 * // PERCENTAGE representation
 * ReturnOverMaxDrawdownCriterion romadPercentage = new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.PERCENTAGE);
 * // Result: 200.0 means return is 200% of the maximum drawdown
 *
 * // MULTIPLICATIVE representation
 * ReturnOverMaxDrawdownCriterion romadMultiplicative = new ReturnOverMaxDrawdownCriterion(
 *         ReturnRepresentation.MULTIPLICATIVE);
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
 * <p>
 * <b>Open positions:</b> When using {@link EquityCurveMode#MARK_TO_MARKET}, the
 * {@link OpenPositionHandling} setting controls whether open positions
 * contribute to the return calculation. {@link EquityCurveMode#REALIZED} always
 * ignores open positions regardless of the requested handling. A null or still
 * open position has no completed equity path and yields the configured
 * representation's neutral value (for example, 1.0 under MULTIPLICATIVE).
 *
 * @see ReturnRepresentation
 * @see ReturnRepresentationPolicy
 */
public class ReturnOverMaxDrawdownCriterion extends AbstractEquityCurveSettingsCriterion {

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
        this(returnRepresentation, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor using a specific equity curve calculation mode.
     *
     * @param equityCurveMode the equity curve mode to use for returns and drawdown
     * @since 0.22.2
     */
    public ReturnOverMaxDrawdownCriterion(EquityCurveMode equityCurveMode) {
        this(ReturnRepresentation.DECIMAL, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor using the provided open position handling.
     *
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public ReturnOverMaxDrawdownCriterion(OpenPositionHandling openPositionHandling) {
        this(ReturnRepresentation.DECIMAL, EquityCurveMode.MARK_TO_MARKET, openPositionHandling);
    }

    /**
     * Constructor using specific equity curve and open position handling.
     *
     * @param equityCurveMode      the equity curve mode to use for returns and
     *                             drawdown
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public ReturnOverMaxDrawdownCriterion(EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this(ReturnRepresentation.DECIMAL, equityCurveMode, openPositionHandling);
    }

    /**
     * Constructor with explicit return representation and equity curve settings.
     *
     * @param returnRepresentation the return representation to use for the output
     *                             ratio
     * @param equityCurveMode      the equity curve mode to use for returns and
     *                             drawdown
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public ReturnOverMaxDrawdownCriterion(ReturnRepresentation returnRepresentation, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        super(equityCurveMode, openPositionHandling);
        this.returnRepresentation = Objects.requireNonNull(returnRepresentation, "returnRepresentation");
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position == null || position.isOpened()) {
            // No completed equity path exists yet, so report the representation's
            // neutral value (0 for DECIMAL, 1 for MULTIPLICATIVE) instead of a raw zero.
            return returnRepresentation.toRepresentationFromRateOfReturn(series.numFactory().zero());
        }
        return series.withReadLock(() -> calculatePosition(series, position));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        return series.withReadLock(() -> calculateTradingRecord(series, tradingRecord));
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

    private Num calculatePosition(BarSeries series, Position position) {
        CashFlow cashFlow = new CashFlow(series, position, equityCurveMode);
        Num maxDrawdown = Drawdown.amount(series, null, cashFlow);
        return toRepresentation(calculateNetReturn(cashFlow), maxDrawdown);
    }

    private Num calculateTradingRecord(BarSeries series, TradingRecord tradingRecord) {
        CashFlow cashFlow = new CashFlow(series, tradingRecord, equityCurveMode, openPositionHandling);
        Num maxDrawdown = Drawdown.amount(series, tradingRecord, cashFlow);
        return toRepresentation(calculateNetReturn(cashFlow), maxDrawdown);
    }

    private Num calculateNetReturn(CashFlow cashFlow) {
        int terminalIndex = cashFlow.getEndIndex();
        if (terminalIndex < cashFlow.getBeginIndex()) {
            return cashFlow.getBarSeries().numFactory().zero();
        }
        Num one = cashFlow.getBarSeries().numFactory().one();
        return cashFlow.getValue(terminalIndex).minus(one);
    }

    private Num toRepresentation(Num netReturn, Num maxDrawdown) {
        if (maxDrawdown.isZero()) {
            return returnRepresentation.toRepresentationFromRateOfReturn(netReturn);
        }
        Num rawRatio = netReturn.dividedBy(maxDrawdown);
        return returnRepresentation.toRepresentationFromRateOfReturn(rawRatio);
    }
}
