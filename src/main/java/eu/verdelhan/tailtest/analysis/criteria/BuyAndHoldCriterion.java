package net.sf.tail.analysis.criteria;

import java.util.ArrayList;
import java.util.List;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.OperationType;
import net.sf.tail.TimeSeries;
import net.sf.tail.Trade;
import net.sf.tail.analysis.evaluator.Decision;

public class BuyAndHoldCriterion implements AnalysisCriterion {

	public double calculate(TimeSeries series, List<Trade> trades) {
		return series.getTick(series.getEnd()).getClosePrice() / series.getTick(series.getBegin()).getClosePrice();
	}

	public double summarize(TimeSeries series, List<Decision> decisions) {
		
		return calculate(series, new ArrayList<Trade>());
	}
	public double calculate(TimeSeries series, Trade trade) {
		if(trade.getEntry().getType() == OperationType.BUY)
			return series.getTick(trade.getExit().getIndex()).getClosePrice() / series.getTick(trade.getEntry().getIndex()).getClosePrice();
		else
			return series.getTick(trade.getEntry().getIndex()).getClosePrice() / series.getTick(trade.getExit().getIndex()).getClosePrice();
		
	}
	
	public String getName() {
		return "Buy and Hold";
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
