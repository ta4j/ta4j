package net.sf.tail.analysis.evaluator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.Runner;
import net.sf.tail.Strategy;
import net.sf.tail.StrategyEvaluator;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.runner.RunnerFactory;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class HigherValueEvaluator implements StrategyEvaluator {

	private static final Logger LOG = Logger.getLogger(HigherValueEvaluator.class);


	private Set<Strategy> strategies;
  
	private TimeSeriesSlicer slicer;

	private AnalysisCriterion criterion;

	private int slicePosition;

	private HashMap<Strategy, Runner> hashRunner;

	public HigherValueEvaluator(RunnerFactory runnerFactory,Set<Strategy> strategies, TimeSeriesSlicer slicer, AnalysisCriterion criterion) {
		this.strategies = strategies;
		this.slicer = slicer;
		this.criterion = criterion;
		this.slicePosition = 0;
		LOG.setLevel(Level.WARN);
		this.hashRunner = new HashMap<Strategy, Runner>();
		
		for (Strategy strategy : strategies) {
			hashRunner.put(strategy, runnerFactory.create(strategy, slicer));
		}
	}

	public Decision evaluate(int slicePosition) {
		Iterator<Strategy> iter = strategies.iterator();
		Strategy bestStrategy = iter.next();

		Runner runner = hashRunner.get(bestStrategy);
		
		Decision bestDecision = new Decision(bestStrategy, slicer,slicePosition, criterion, runner.run(slicePosition), runner);

		
		while (iter.hasNext()) {
			Strategy strategy = iter.next();
			runner = hashRunner.get(strategy);
			
			Decision decision = new Decision(strategy, slicer,slicePosition, criterion, runner.run(slicePosition), runner);
			double value = decision.evaluateCriterion();
			LOG.info(String.format("For %s, criterion %s, gave %.3f", strategy, criterion.getClass().getSimpleName(),
					value));
			if (value > bestDecision.evaluateCriterion()) {
				//TODO O que fazer com o empate entre criterions??
				bestDecision = decision;
			}
		}
		return bestDecision;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((criterion == null) ? 0 : criterion.hashCode());
		result = prime * result + ((hashRunner == null) ? 0 : hashRunner.hashCode());
		result = prime * result + slicePosition;
		result = prime * result + ((slicer == null) ? 0 : slicer.hashCode());
		result = prime * result + ((strategies == null) ? 0 : strategies.hashCode());
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
		final HigherValueEvaluator other = (HigherValueEvaluator) obj;
		if (criterion == null) {
			if (other.criterion != null)
				return false;
		} else if (!criterion.equals(other.criterion))
			return false;
		if (hashRunner == null) {
			if (other.hashRunner != null)
				return false;
		} else if (!hashRunner.equals(other.hashRunner))
			return false;
		if (slicePosition != other.slicePosition)
			return false;
		if (slicer == null) {
			if (other.slicer != null)
				return false;
		} else if (!slicer.equals(other.slicer))
			return false;
		if (strategies == null) {
			if (other.strategies != null)
				return false;
		} else if (!strategies.equals(other.strategies))
			return false;
		return true;
	}
}
