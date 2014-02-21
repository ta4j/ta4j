package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import java.util.List;

/**
 * A fixed transaction cost.
 * E.g.: 0.5 for $0.5 per operation (buy or sell)
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
