package eu.verdelhan.tailtest.analysis.criteria;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import eu.verdelhan.tailtest.AnalysisCriterion;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.analysis.evaluator.Decision;

public class RewardRiskRatioCriterion implements AnalysisCriterion {

	private AnalysisCriterion totalProfit = new TotalProfitCriterion();

	private MaximumDrawDownCriterion maxDrawnDown = new MaximumDrawDownCriterion();

	public double calculate(TimeSeries series, List<Trade> trades) {
		return totalProfit.calculate(series, trades) / maxDrawnDown.calculate(series, trades);
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
		return "Reward Risk Ratio";
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
