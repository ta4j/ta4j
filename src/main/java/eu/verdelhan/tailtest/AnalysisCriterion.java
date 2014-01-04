package eu.verdelhan.tailtest;

import java.util.List;

import eu.verdelhan.tailtest.analysis.evaluator.Decision;

public interface AnalysisCriterion {

    double calculate(TimeSeries series, Trade trade);

    double calculate(TimeSeries series, List<Trade> trades);

    double summarize(TimeSeries series, List<Decision> decisions);

    String getName();
}