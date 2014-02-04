package eu.verdelhan.ta4j.analysis.evaluator;

import eu.verdelhan.ta4j.analysis.evaluator.HigherValueEvaluator;
import eu.verdelhan.ta4j.analysis.evaluator.Decision;
import static org.junit.Assert.assertEquals;

import java.util.HashSet;

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.analysis.criteria.TotalProfitCriterion;
import eu.verdelhan.ta4j.runner.HistoryRunnerFactory;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.series.RegularSlicer;
import eu.verdelhan.ta4j.strategy.AlwaysOperateStrategy;
import eu.verdelhan.ta4j.mocks.MockStrategy;

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

		assertEquals(alwaysStrategy, decision.getStrategy());
	}

	@Test
	public void bestShouldBeBuyAndHoldOnLoss() {
		DateTime date = new DateTime();
		MockTimeSeries series = new MockTimeSeries(new double[] { 6.0, 3.0, 6.0, 6.0 }, new DateTime[]{date, date, date, date});
		

		HigherValueEvaluator evaluator = new HigherValueEvaluator(new HistoryRunnerFactory(), strategies, new RegularSlicer(series, new Period().withYears(2000)), new TotalProfitCriterion());
		Decision decision = evaluator.evaluate(0);

		assertEquals(buyAndHoldStrategy, decision.getStrategy());
	}
}
