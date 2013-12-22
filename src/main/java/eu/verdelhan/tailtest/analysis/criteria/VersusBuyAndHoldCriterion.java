package net.sf.tail.analysis.criteria;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.Operation;
import net.sf.tail.OperationType;
import net.sf.tail.TimeSeries;
import net.sf.tail.Trade;
import net.sf.tail.analysis.evaluator.Decision;

public class VersusBuyAndHoldCriterion implements AnalysisCriterion {

	private AnalysisCriterion criterion;

	public VersusBuyAndHoldCriterion(AnalysisCriterion criterion) {
		this.criterion = criterion;
	}
	
	public double calculate(TimeSeries series, List<Trade> trades) {
		List<Trade> fakeTrades = new ArrayList<Trade>();
		fakeTrades.add(new Trade(new Operation(series.getBegin(), OperationType.BUY), new Operation(series.getEnd(), OperationType.SELL)));
		
		return criterion.calculate(series, trades) / criterion.calculate(series, fakeTrades);
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
		return "VB&H " + criterion.getName();
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
		if(!this.criterion.equals(((VersusBuyAndHoldCriterion) obj).criterion))
			return false;
		return true;
	}
	

}
