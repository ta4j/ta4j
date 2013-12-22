package net.sf.tail.analysis.criteria;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.TimeSeries;
import net.sf.tail.Trade;
import net.sf.tail.analysis.evaluator.Decision;

public class AverageProfitCriterion implements AnalysisCriterion {

	private AnalysisCriterion totalProfit = new TotalProfitCriterion();

	private AnalysisCriterion numberOfTicks = new NumberOfTicksCriterion();

	public double calculate(TimeSeries series, List<Trade> trades) {
		double ticks = numberOfTicks.calculate(series, trades);
		if(ticks == 0)
			return 1;
		return Math.pow(totalProfit.calculate(series, trades), 1d /ticks );
	}

	public double summarize(TimeSeries series, List<Decision> decisions) {
		List<Trade> trades = new LinkedList<Trade>();

		for (Decision decision : decisions) {
			trades.addAll(decision.getTrades());
		}
		return calculate(series, trades);
	}

	public double calculate(TimeSeries series, Trade trade) {
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(trade);
		return calculate(series, trades);
		
	}
	
	public String getName() {
		return "Average Profit";
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
