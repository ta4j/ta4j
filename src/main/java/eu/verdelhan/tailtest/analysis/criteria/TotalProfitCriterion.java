package net.sf.tail.analysis.criteria;

import java.util.LinkedList;
import java.util.List;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.OperationType;
import net.sf.tail.TimeSeries;
import net.sf.tail.Trade;
import net.sf.tail.analysis.evaluator.Decision;

public class TotalProfitCriterion implements AnalysisCriterion {

	public double calculate(TimeSeries series, List<Trade> trades) {
		double value = 1d;
		for (Trade trade : trades) {
			value *= calculateProfit(series, trade);
		}
		return value;
	}

	public double summarize(TimeSeries series, List<Decision> decisions) {
		List<Trade> trades = new LinkedList<Trade>();

		for (Decision decision : decisions) {
			trades.addAll(decision.getTrades());
		}

		return calculate(series, trades);
	}

	public double calculate(TimeSeries series, Trade trade) {
		return calculateProfit(series, trade);

	}

	private double calculateProfit(TimeSeries series, Trade trade) {
		double exitClosePrice = series.getTick(trade.getExit().getIndex()).getClosePrice();
		double entryClosePrice = series.getTick(trade.getEntry().getIndex()).getClosePrice();

		if (trade.getEntry().getType() == OperationType.BUY) {

			return exitClosePrice / entryClosePrice;
		}

		return entryClosePrice / exitClosePrice;

	}
	
	public String getName() {
		return "Total Profit";
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
