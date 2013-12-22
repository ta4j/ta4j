package net.sf.tail;

import java.util.List;

import net.sf.tail.analysis.evaluator.Decision;

public interface AnalysisCriterion {

	double calculate (TimeSeries series, Trade trade);
	
	double calculate(TimeSeries series, List<Trade> trades);

	double summarize(TimeSeries series, List<Decision> decisions);
	
	String getName();
	
}
