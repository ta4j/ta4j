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
 * A linear transaction cost criterion.
 * <p>
 * That criterion calculate the transaction cost according to an initial traded amount
 * and a linear function defined by a and b (a * x + b).
 */
public class LinearTransactionCostCriterion extends AbstractAnalysisCriterion {

    private double initialAmount;

    private double a;
    private double b;

    private TotalProfitCriterion profit;

    /**
     * Constructor.
     * (a * x)
     * @param initialAmount the initially traded amount
     * @param a the a coefficient (e.g. 0.005 for 0.5% per {@link Operation operation})
     */
    public LinearTransactionCostCriterion(double initialAmount, double a) {
        this(initialAmount, a, 0);
    }

    /**
     * Constructor.
     * (a * x + b)
     * @param initialAmount the initially traded amount
     * @param a the a coefficient (e.g. 0.005 for 0.5% per {@link Operation operation})
     * @param b the b constant (e.g. 0.2 for $0.2 per {@link Operation operation})
     */
    public LinearTransactionCostCriterion(double initialAmount, double a, double b) {
        this.initialAmount = initialAmount;
        this.a = a;
        this.b = b;
        profit = new TotalProfitCriterion();
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        return getTradeCost(series, trade, initialAmount);
    }

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        double totalCosts = 0d;
        double tradedAmount = initialAmount;
        for (Trade trade : trades) {
            double tradeCost = getTradeCost(series, trade, tradedAmount);
            totalCosts += tradeCost;
            // To calculate the new traded amount:
            //    - Remove the cost of the first operation
            //    - Multiply by the profit ratio
            tradedAmount = (tradedAmount - tradeCost) * profit.calculate(series, trade);
        }
        return totalCosts;
    }

    @Override
    public boolean betterThan(double criterionValue1, double criterionValue2) {
        return criterionValue1 < criterionValue2;
    }

    /**
     * @param operation a trade operation
     * @param tradedAmount the traded amount for the operation
     * @return the absolute operation cost
     */
    private double getOperationCost(Operation operation, double tradedAmount) {
        double operationCost = 0d;
        if (operation != null) {
            return a * tradedAmount + b;
        }
        return operationCost;
    }

    /**
     * @param series the time series
     * @param trade a trade
     * @param initialAmount the initially traded amount for the trade
     * @return the absolute total cost of all operations in the trade
     */
    private double getTradeCost(TimeSeries series, Trade trade, double initialAmount) {
        double totalTradeCost = 0d;
        if (trade != null) {
            if (trade.getEntry() != null) {
                totalTradeCost = getOperationCost(trade.getEntry(), initialAmount);
                if (trade.getExit() != null) {
                    // To calculate the new traded amount:
                    //    - Remove the cost of the first operation
                    //    - Multiply by the profit ratio
                    double newTradedAmount = (initialAmount - totalTradeCost) * profit.calculate(series, trade);
                    totalTradeCost += getOperationCost(trade.getExit(), newTradedAmount);
                }
            }
        }
        return totalTradeCost;
    }
}
