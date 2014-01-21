package eu.verdelhan.tailtest.analysis.criteria;

import eu.verdelhan.tailtest.AnalysisCriterion;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.analysis.evaluator.Decision;
import java.util.ArrayList;
import java.util.List;

public class LinearTransactionCostsCriterion implements AnalysisCriterion {

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

    @Override
    public double summarize(TimeSeries series, List<Decision> decisions) {
        List<Trade> trades = new ArrayList<Trade>();
        for (Decision decision : decisions) {
            trades.addAll(decision.getTrades());

        }
        return calculate(series, trades);
    }

    @Override
    public String getName() {
        return "Linear Transaction Costs";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + (this.getClass().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        return true;
    }
}
