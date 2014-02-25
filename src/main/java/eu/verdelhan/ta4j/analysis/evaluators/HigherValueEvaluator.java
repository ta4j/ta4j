/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package eu.verdelhan.ta4j.analysis.evaluators;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Runner;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.StrategyEvaluator;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.runners.RunnerFactory;
import java.util.HashMap;
import java.util.Set;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Higher value evaluator.
 * <p>
 */
public class HigherValueEvaluator implements StrategyEvaluator {

    private static final Logger LOG = Logger.getLogger(HigherValueEvaluator.class);

    private Set<Strategy> strategies;

    private TimeSeriesSlicer slicer;

    private AnalysisCriterion criterion;

    private HashMap<Strategy, Runner> hashRunner;

	/**
	 * Constructor.
	 * @param runnerFactory the runner factory
	 * @param strategies a set of strategies to be evaluated
	 * @param slicer a time series slicer
	 * @param criterion an analysis criterion
	 */
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
