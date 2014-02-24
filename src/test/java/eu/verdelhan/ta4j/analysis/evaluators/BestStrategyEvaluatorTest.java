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

import eu.verdelhan.ta4j.analysis.evaluators.HigherValueEvaluator;
import eu.verdelhan.ta4j.analysis.evaluators.Decision;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.analysis.criteria.TotalProfitCriterion;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.runners.HistoryRunnerFactory;
import eu.verdelhan.ta4j.series.RegularSlicer;
import eu.verdelhan.ta4j.strategies.AlwaysOperateStrategy;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.*;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class BestStrategyEvaluatorTest {

	private Operation[] enter;

	private Operation[] exit;

	private AlwaysOperateStrategy alwaysStrategy;

	private MockStrategy buyAndHoldStrategy;

	private HashSet<Strategy> strategies;

	@Before
	public void setUp() {
		enter = new Operation[] { new Operation(0, OperationType.BUY), null, null, null };
		exit = new Operation[] { null, null, null, new Operation(4, OperationType.SELL) };
		alwaysStrategy = new AlwaysOperateStrategy();
		buyAndHoldStrategy = new MockStrategy(enter, exit);
		strategies = new HashSet<Strategy>();
		strategies.add(alwaysStrategy);
		strategies.add(buyAndHoldStrategy);
	}

	@Test
	public void bestShouldBeAlwaysOperateOnProfit() {
		DateTime date = new DateTime();
		MockTimeSeries series = new MockTimeSeries(new double[] { 6.0, 9.0, 6.0, 6.0 }, new DateTime[]{date, date, date, date});
		
		HigherValueEvaluator evaluator = new HigherValueEvaluator(new HistoryRunnerFactory(), strategies, new RegularSlicer(series, new Period().withYears(2000)), new TotalProfitCriterion());
		Decision decision = evaluator.evaluate(0);

		assertThat(decision.getStrategy()).isEqualTo(alwaysStrategy);
	}

	@Test
	public void bestShouldBeBuyAndHoldOnLoss() {
		DateTime date = new DateTime();
		MockTimeSeries series = new MockTimeSeries(new double[] { 6.0, 3.0, 6.0, 6.0 }, new DateTime[]{date, date, date, date});
		

		HigherValueEvaluator evaluator = new HigherValueEvaluator(new HistoryRunnerFactory(), strategies, new RegularSlicer(series, new Period().withYears(2000)), new TotalProfitCriterion());
		Decision decision = evaluator.evaluate(0);

		assertThat(decision.getStrategy()).isEqualTo(buyAndHoldStrategy);
	}
}
