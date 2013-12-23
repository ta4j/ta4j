package eu.verdelhan.tailtest.analysis.criteria;

import java.util.ArrayList;
import java.util.List;

import eu.verdelhan.tailtest.AnalysisCriterion;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.analysis.evaluator.Decision;

public class BrazilianTransactionCostsCriterion implements AnalysisCriterion {

	public double calculate(TimeSeries series, Trade trade) {
		return 40d;
	}

	public double calculate(TimeSeries series, List<Trade> trades) {
		return 40d * trades.size();
	}

	public double summarize(TimeSeries series, List<Decision> decisions) {
		List<Trade> trades = new ArrayList<Trade>();
		for (Decision decision : decisions) {
			trades.addAll(decision.getTrades());
			
		}
		return calculate(series, trades);
	}
	
	public String getName() {
		return "Brazilian Transaction Costs";
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (this.getClass().hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		return true;
	}

}
