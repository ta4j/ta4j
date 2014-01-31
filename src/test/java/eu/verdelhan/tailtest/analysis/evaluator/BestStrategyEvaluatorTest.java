package eu.verdelhan.tailtest.analysis.evaluator;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;

import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.Strategy;
import eu.verdelhan.tailtest.analysis.criteria.TotalProfitCriterion;
import eu.verdelhan.tailtest.runner.HistoryRunnerFactory;
import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import eu.verdelhan.tailtest.series.RegularSlicer;
import eu.verdelhan.tailtest.strategy.AlwaysOperateStrategy;
import eu.verdelhan.tailtest.strategy.FakeStrategy;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class BestStrategyEvaluatorTest {

	private Operation[] enter;

	private Operation[] exit;

	private AlwaysOperateStrategy alwaysStrategy;

	private FakeStrategy buyAndHoldStrategy;

	private HashSet<Strategy> strategies;

	@Before
	public void setUp() {
		enter = new Operation[] { new Operation(0, OperationType.BUY), null, null, null };
		exit = new Operation[] { null, null, null, new Operation(4, OperationType.SELL) };
		alwaysStrategy = new AlwaysOperateStrategy();
		buyAndHoldStrategy = new FakeStrategy(enter, exit);
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
