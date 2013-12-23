package eu.verdelhan.tailtest.analysis.evaluator;

import java.util.Set;

import eu.verdelhan.tailtest.AnalysisCriterion;
import eu.verdelhan.tailtest.Strategy;
import eu.verdelhan.tailtest.StrategyEvaluator;
import eu.verdelhan.tailtest.TimeSeriesSlicer;
import eu.verdelhan.tailtest.runner.RunnerFactory;

public class HigherValueEvaluatorFactory implements StrategyEvaluatorFactory {

	public StrategyEvaluator create(RunnerFactory runnerFactory, Set<Strategy> strategies, TimeSeriesSlicer slicer,
			AnalysisCriterion criterion) {
		return new HigherValueEvaluator(runnerFactory,strategies,slicer,criterion);
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
