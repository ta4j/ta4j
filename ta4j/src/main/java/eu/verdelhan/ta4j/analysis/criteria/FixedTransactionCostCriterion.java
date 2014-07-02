/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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
package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import java.util.List;

/**
 * A fixed transaction cost.
 * E.g.: 0.5 for $0.5 per {@link Operation operation} (buy or sell).
 * @todo Add a criterion for linear (a * x + b) transaction cost
 */
public class FixedTransactionCostCriterion extends AbstractAnalysisCriterion {

    private double transactionCost;

    /**
     * Constructor.
     * @param transactionCost an absolute per-transaction cost (e.g. 0.5 for $0.5)
     */
    public FixedTransactionCostCriterion(double transactionCost) {
        this.transactionCost = transactionCost;
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        return getTradeCost(trade);
    }

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        double totalCosts = 0d;
        for (Trade trade : trades) {
            totalCosts += getTradeCost(trade);
        }
        return totalCosts;
    }

    @Override
    public boolean betterThan(double criterionValue1, double criterionValue2) {
        return criterionValue1 < criterionValue2;
    }

    /**
     * @param trade a trade
     * @return the total cost of all operations in the trade
     */
    private double getTradeCost(Trade trade) {
        double totalTradeCost = 0d;
        if (trade != null) {
            if (trade.getEntry() != null) {
                totalTradeCost += transactionCost;
            }
            if (trade.getExit() != null) {
                totalTradeCost += transactionCost;
            }
        }
        return totalTradeCost;
    }
}
