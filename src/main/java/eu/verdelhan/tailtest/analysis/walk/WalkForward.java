package net.sf.tail.analysis.walk;

import java.util.LinkedList;
import java.util.List;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.StrategiesSet;
import net.sf.tail.StrategyEvaluator;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.Walker;
import net.sf.tail.analysis.evaluator.Decision;
import net.sf.tail.analysis.evaluator.StrategyEvaluatorFactory;
import net.sf.tail.report.Report;
import net.sf.tail.runner.RunnerFactory;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class WalkForward implements Walker {

	
	
	private static final Logger LOG = Logger.getLogger(WalkForward.class);
	private StrategyEvaluatorFactory evaluatorFactory;
	private RunnerFactory runnerFactory;
	


	public WalkForward(StrategyEvaluatorFactory evaluatorFactory,RunnerFactory runnerFactory) {
		this.evaluatorFactory = evaluatorFactory;
		this.runnerFactory = runnerFactory;
		LOG.setLevel(Level.WARN);
	}

	public Report walk(StrategiesSet strategiesSet, TimeSeriesSlicer slicer, AnalysisCriterion criterion) {

		LOG.info("Running strategies");
		List<Decision> decisions = new LinkedList<Decision>();

		StrategyEvaluator evaluator = evaluatorFactory.create(runnerFactory, strategiesSet.getStrategies(), slicer, criterion);
		Decision lastDecision = evaluator.evaluate(0);

		LOG.info("First best decision calculated: " + lastDecision);

		for (int i = 1; i < slicer.getSlices(); i++) {
			Decision bestAppliedForCurrentSeries = lastDecision.applyFor(i);
			LOG.info(String
					.format("Applying last best decision for time series %d: %s", i, bestAppliedForCurrentSeries));
			decisions.add(bestAppliedForCurrentSeries);

			lastDecision = evaluator.evaluate(i);
			LOG.info("Best decision for period " + i + ": " + lastDecision);

		}
		return new Report(strategiesSet, criterion, slicer, decisions);
	}

}
