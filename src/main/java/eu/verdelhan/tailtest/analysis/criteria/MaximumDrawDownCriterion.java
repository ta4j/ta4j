package eu.verdelhan.tailtest.analysis.criteria;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import eu.verdelhan.tailtest.AnalysisCriterion;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.analysis.evaluator.Decision;
import eu.verdelhan.tailtest.flow.CashFlow;

public class MaximumDrawDownCriterion implements AnalysisCriterion {

	public double calculate(TimeSeries series, List<Trade> trades) {
		double maximumDrawDown = 0d;
		double maxPeak = 0;
		CashFlow cashFlow = new CashFlow(series, trades);

		for (int i = series.getBegin(); i <= series.getEnd(); i++) {
			double value = cashFlow.getValue(i);
			if (value > maxPeak) {
				maxPeak = value;
			}

			double drawDown = (maxPeak - value) / maxPeak;
			if (drawDown > maximumDrawDown) {
				maximumDrawDown = drawDown;
				// absolute maximumDrawDown.
				// should it be maximumDrawDown = drawDown/maxPeak ?
			}
		}
		return maximumDrawDown;
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
		return "Maximum Draw Down";
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
