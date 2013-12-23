package eu.verdelhan.tailtest.analysis.evaluator;

import java.util.Set;

import eu.verdelhan.tailtest.AnalysisCriterion;
import eu.verdelhan.tailtest.Strategy;
import eu.verdelhan.tailtest.StrategyEvaluator;
import eu.verdelhan.tailtest.TimeSeriesSlicer;
import eu.verdelhan.tailtest.runner.RunnerFactory;

public interface StrategyEvaluatorFactory {
	StrategyEvaluator create(RunnerFactory runnerFactory,Set<Strategy> strategies, TimeSeriesSlicer slicer, AnalysisCriterion criterion);
}
