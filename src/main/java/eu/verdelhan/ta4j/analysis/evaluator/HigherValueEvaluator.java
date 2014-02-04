package eu.verdelhan.ta4j.analysis.evaluator;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Runner;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.StrategyEvaluator;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.runner.RunnerFactory;
import java.util.HashMap;
import java.util.Set;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class HigherValueEvaluator implements StrategyEvaluator {

    private static final Logger LOG = Logger.getLogger(HigherValueEvaluator.class);

    private Set<Strategy> strategies;

    private TimeSeriesSlicer slicer;

    private AnalysisCriterion criterion;

    private HashMap<Strategy, Runner> hashRunner;

    public HigherValueEvaluator(RunnerFactory runnerFactory, Set<Strategy> strategies, TimeSeriesSlicer slicer, AnalysisCriterion criterion) {
        this.strategies = strategies;
        this.slicer = slicer;
        this.criterion = criterion;
        LOG.setLevel(Level.WARN);
        hashRunner = new HashMap<Strategy, Runner>();

        for (Strategy strategy : strategies) {
            hashRunner.put(strategy, runnerFactory.create(strategy, slicer));
        }
    }

    @Override
    public Decision evaluate(int slicePosition) {
        Strategy bestStrategy = strategies.iterator().next();

		// Getting the runner of the strategy
        Runner runner = hashRunner.get(bestStrategy);
        Decision bestDecision = new Decision(bestStrategy, slicer, slicePosition, criterion, runner.run(slicePosition), runner);

        for (Strategy strategy : strategies) {
            runner = hashRunner.get(strategy);

            Decision decision = new Decision(strategy, slicer, slicePosition, criterion, runner.run(slicePosition), runner);
            double value = decision.evaluateCriterion();
            LOG.info(String.format("For %s, criterion %s, gave %.3f", strategy, criterion.getClass().getSimpleName(), value));
            if (value > bestDecision.evaluateCriterion()) {
                // @todo Don't know how to manage a link between 2 criteria
                bestDecision = decision;
            }
        }
        return bestDecision;
    }
}
