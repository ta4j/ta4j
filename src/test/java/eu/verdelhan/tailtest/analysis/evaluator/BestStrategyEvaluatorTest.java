package net.sf.tail.analysis.evaluator;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;

import net.sf.tail.Operation;
import net.sf.tail.OperationType;
import net.sf.tail.Strategy;
import net.sf.tail.analysis.criteria.TotalProfitCriterion;
import net.sf.tail.runner.HistoryRunnerFactory;
import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.series.RegularSlicer;
import net.sf.tail.strategy.AlwaysOperateStrategy;
import net.sf.tail.strategy.FakeStrategy;

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
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 6.0, 9.0, 6.0, 6.0 }, new DateTime[]{date, date, date, date});
		
		HigherValueEvaluator evaluator = new HigherValueEvaluator(new HistoryRunnerFactory(), strategies, new RegularSlicer(series, new Period().withYears(2000)), new TotalProfitCriterion());
		Decision decision = evaluator.evaluate(0);

		assertEquals(alwaysStrategy, decision.getStrategy());
	}

	@Test
	public void bestShouldBeBuyAndHoldOnLoss() {
		DateTime date = new DateTime();
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 6.0, 3.0, 6.0, 6.0 }, new DateTime[]{date, date, date, date});
		

		HigherValueEvaluator evaluator = new HigherValueEvaluator(new HistoryRunnerFactory(), strategies, new RegularSlicer(series, new Period().withYears(2000)), new TotalProfitCriterion());
		Decision decision = evaluator.evaluate(0);

		assertEquals(buyAndHoldStrategy, decision.getStrategy());
	}
}
