package net.sf.tail.analysis.evaluator;

import java.util.Set;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.Strategy;
import net.sf.tail.StrategyEvaluator;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.runner.RunnerFactory;

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
