/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.Objects;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.num.Num;

/**
 * A linear transaction cost criterion.
 *
 * <p>
 * Calculates the transaction cost according to an initial traded amount and a
 * linear function defined by a and b (a * x + b).
 */
public class LinearTransactionCostCriterion extends AbstractAnalysisCriterion {

    private final OpenPositionHandling openPositionHandling;

    private final double initialAmount;

    private final double a;
    private final double b;

    private final GrossReturnCriterion grossReturn;

    /**
     * Constructor. (a * x)
     *
     * @param initialAmount the initially traded amount
     * @param a             the a coefficient (e.g. 0.005 for 0.5% per {@link Trade
     *                      trade})
     */
    public LinearTransactionCostCriterion(double initialAmount, double a) {
        this(initialAmount, a, 0);
    }

    /**
     * Constructor. (a * x + b)
     *
     * @param initialAmount the initially traded amount
     * @param a             the a coefficient (e.g. 0.005 for 0.5% per {@link Trade
     *                      trade})
     * @param b             the b constant (e.g. 0.2 for $0.2 per {@link Trade
     *                      trade})
     */
    public LinearTransactionCostCriterion(double initialAmount, double a, double b) {
        this(initialAmount, a, b, OpenPositionHandling.IGNORE);
    }

    /**
     * Creates a cost criterion with configurable bounded-record open handling.
     * Explicit-window context overrides this setting. Unbounded records retain
     * their historical open-entry cost calculation.
     *
     * @param initialAmount        the initially traded amount
     * @param a                    the proportional trade cost coefficient
     * @param b                    the fixed trade cost
     * @param openPositionHandling handling at a bounded record's logical end
     * @since 0.26.1
     */
    public LinearTransactionCostCriterion(double initialAmount, double a, double b,
            OpenPositionHandling openPositionHandling) {
        this.openPositionHandling = Objects.requireNonNull(openPositionHandling, "openPositionHandling");
        this.initialAmount = initialAmount;
        this.a = a;
        this.b = b;
        // Use MULTIPLICATIVE (1-based) for internal calculations since we multiply
        // amounts
        grossReturn = new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return getTradeCost(series, position, series.numFactory().numOf(initialAmount));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        tradingRecord = boundedTradingRecord(series, tradingRecord, openPositionHandling);
        Num totalCosts = series.numFactory().zero();
        Num tradedAmount = series.numFactory().numOf(initialAmount);

        for (Position position : tradingRecord.getPositions()) {
            Num tradeCost = getTradeCost(series, position, tradedAmount);
            totalCosts = totalCosts.plus(tradeCost);
            // To calculate the new traded amount:
            // - Remove the cost of the *first* trade
            // - Multiply by the profit ratio
            // - Remove the cost of the *second* trade
            tradedAmount = tradedAmount.minus(getTradeCost(position.getEntry(), tradedAmount));
            tradedAmount = tradedAmount.multipliedBy(grossReturn.calculate(series, position));
            tradedAmount = tradedAmount.minus(getTradeCost(position.getExit(), tradedAmount));
        }

        // Special case: if the current position is open
        Position currentPosition = tradingRecord.getCurrentPosition();
        if (currentPosition.isOpened()) {
            totalCosts = totalCosts.plus(getTradeCost(currentPosition.getEntry(), tradedAmount));
        }

        return totalCosts;
    }

    /** The lower the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }

    /**
     * @param trade        the trade
     * @param tradedAmount the amount of the trade
     * @return the absolute trade cost
     */
    private Num getTradeCost(Trade trade, Num tradedAmount) {
        final var numFactory = tradedAmount.getNumFactory();
        Num tradeCost = numFactory.zero();
        if (trade != null) {
            return numFactory.numOf(a).multipliedBy(tradedAmount).plus(numFactory.numOf(b));
        }
        return tradeCost;
    }

    /**
     * @param series        the bar series
     * @param position      the position
     * @param initialAmount the initially traded amount for the position
     * @return the absolute total cost of all trades in the position
     */
    private Num getTradeCost(BarSeries series, Position position, Num initialAmount) {
        Num totalTradeCost = series.numFactory().zero();
        if (position != null && position.getEntry() != null) {
            totalTradeCost = getTradeCost(position.getEntry(), initialAmount);
            if (position.getExit() != null) {
                // To calculate the new traded amount:
                // - Remove the cost of the first trade
                // - Multiply by the profit ratio
                Num newTradedAmount = initialAmount.minus(totalTradeCost)
                        .multipliedBy(grossReturn.calculate(series, position));
                totalTradeCost = totalTradeCost.plus(getTradeCost(position.getExit(), newTradedAmount));
            }
        }
        return totalTradeCost;
    }
}
