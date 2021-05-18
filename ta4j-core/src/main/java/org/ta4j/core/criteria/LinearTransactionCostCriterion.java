/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.num.Num;

/**
 * A linear transaction cost criterion.
 *
 * Calculates the transaction cost according to an initial traded amount and a
 * linear function defined by a and b (a * x + b).
 */
public class LinearTransactionCostCriterion extends AbstractAnalysisCriterion {

    private double initialAmount;

    private double a;
    private double b;

    private GrossReturnCriterion grossReturn;

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
        this.initialAmount = initialAmount;
        this.a = a;
        this.b = b;
        grossReturn = new GrossReturnCriterion();
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return getTradeCost(series, position, series.numOf(initialAmount));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num totalCosts = series.numOf(0);
        Num tradedAmount = series.numOf(initialAmount);

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
        Num tradeCost = tradedAmount.numOf(0);
        if (trade != null) {
            return tradedAmount.numOf(a).multipliedBy(tradedAmount).plus(tradedAmount.numOf(b));
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
        Num totalTradeCost = series.numOf(0);
        if (position != null) {
            if (position.getEntry() != null) {
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
        }
        return totalTradeCost;
    }
}
