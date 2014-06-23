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
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.StrategyEvaluator;
import eu.verdelhan.ta4j.TimeSeries;
import java.util.Set;

/**
 * Higher value evaluator.
 * <p>
 */
public class HigherValueEvaluator implements StrategyEvaluator {

    private Set<Strategy> strategies;

    private TimeSeries series;

    private AnalysisCriterion criterion;

    /**
     * Constructor.
     * @param strategies a set of strategies to be evaluated
     * @param series a time series
     * @param criterion an analysis criterion
     */
    public HigherValueEvaluator(Set<Strategy> strategies, TimeSeries series, AnalysisCriterion criterion) {
        this.strategies = strategies;
        this.series = series;
        this.criterion = criterion;
    }

    @Override
    public Decision evaluate() {
        Strategy bestStrategy = strategies.iterator().next();

        // Getting the runner of the strategy
        Decision bestDecision = new Decision(bestStrategy, series, criterion);

        for (Strategy strategy : strategies) {

            Decision decision = new Decision(strategy, series, criterion);
            double value = decision.evaluateCriterion();
//          System.out.println(String.format("For %s, criterion %s, gave %.3f", strategy, criterion.getClass().getSimpleName(), value));
            if (value > bestDecision.evaluateCriterion()) {
                // @todo Don't know how to manage a link between 2 criteria
                bestDecision = decision;
            }
        }
        return bestDecision;
    }
}
