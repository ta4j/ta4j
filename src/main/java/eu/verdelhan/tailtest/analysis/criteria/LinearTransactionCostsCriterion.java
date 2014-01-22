package eu.verdelhan.tailtest.analysis.criteria;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.Trade;
import java.util.List;

public class LinearTransactionCostsCriterion extends AbstractAnalysisCriterion {

	private double a;
	private double b;

	/**
	 * Constructor
	 * (a * x + b)
	 * @param a a
	 * @param b b
	 */
	public LinearTransactionCostsCriterion(double a, double b)
	{
		this.a = a;
		this.b = b;
	}

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        return a + b;
    }

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        return a * trades.size() + b;
    }
}
