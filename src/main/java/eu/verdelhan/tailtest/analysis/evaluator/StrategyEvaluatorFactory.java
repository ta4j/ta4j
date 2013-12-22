package net.sf.tail.analysis.evaluator;

import java.util.Set;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.Strategy;
import net.sf.tail.StrategyEvaluator;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.runner.RunnerFactory;

public interface StrategyEvaluatorFactory {
	StrategyEvaluator create(RunnerFactory runnerFactory,Set<Strategy> strategies, TimeSeriesSlicer slicer, AnalysisCriterion criterion);
}
