package net.sf.tail.analysis.walk;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.Operation;
import net.sf.tail.OperationType;
import net.sf.tail.Strategy;
import net.sf.tail.TimeSeries;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.Walker;
import net.sf.tail.analysis.criteria.TotalProfitCriterion;
import net.sf.tail.analysis.evaluator.Decision;
import net.sf.tail.analysis.evaluator.HigherValueEvaluatorFactory;
import net.sf.tail.runner.HistoryRunnerFactory;
import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.series.RegularSlicer;
import net.sf.tail.strategiesSet.JavaStrategiesSet;
import net.sf.tail.strategy.FakeStrategy;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class WalkForwardTest {

	private Strategy firstStrategy;

	private Strategy secondStrategy;

	private AnalysisCriterion criteria;

	private TimeSeries series;

	private Operation[] enter;

	private Operation[] exit;

	private Set<Strategy> strategies;

	@Before
	public void setUp() {
		double[] data = new double[] { 5d, 1d, 8d, 3d, 12d, 20d, 4d, 3d, 30d, 20d, 15d, 32d, 18d, 15d };

		DateTime date = new DateTime(0);
		DateTime[] dates = new DateTime[] { date.withYear(2000), date.withYear(2000), date.withYear(2000),
				date.withYear(2000), date.withYear(2000), date.withYear(2001), date.withYear(2001),
				date.withYear(2001), date.withYear(2001), date.withYear(2001), date.withYear(2002),
				date.withYear(2002), date.withYear(2002), date.withYear(2002) };
		this.strategies = new HashSet<Strategy>();
		this.criteria = new TotalProfitCriterion();
		this.series = new SampleTimeSeries(data, dates);

		enter = new Operation[] { new Operation(0, OperationType.BUY), null, null, null, null, null,
				new Operation(6, OperationType.BUY), null, null, null, null, new Operation(11, OperationType.BUY),
				null, null };
		exit = new Operation[] { null, null, null, new Operation(3, OperationType.SELL), null, null, null, null,
				new Operation(8, OperationType.SELL), null, null, null, null, new Operation(13, OperationType.SELL) };
		this.firstStrategy = new FakeStrategy(enter, exit);
		strategies.add(firstStrategy);

		enter = new Operation[] { new Operation(0, OperationType.BUY), null, null, null, null, null, null, null,
				new Operation(8, OperationType.BUY), null, null, new Operation(11, OperationType.BUY), null, null };
		exit = new Operation[] { null, null, new Operation(2, OperationType.SELL), null, null, null, null, null, null,
				null, new Operation(10, OperationType.SELL), null, new Operation(12, OperationType.SELL), null };
		this.secondStrategy = new FakeStrategy(enter, exit);
		strategies.add(this.secondStrategy);
	}

	@Test
	public void testWalk() {
		
		Period period = new Period().withYears(1);
		TimeSeriesSlicer splittedSeries = new RegularSlicer(series, period);

		
		Walker walk = new WalkForward(new HigherValueEvaluatorFactory(), new HistoryRunnerFactory());
		
		List<Decision> decisions = walk.walk(new JavaStrategiesSet( strategies), splittedSeries, criteria).getDecisions();
		assertEquals(secondStrategy, decisions.get(0).getStrategy());
		assertEquals(15d/30, decisions.get(0).evaluateCriterion());
		
		assertEquals(firstStrategy, decisions.get(1).getStrategy());
		assertEquals(15d/32, decisions.get(1).evaluateCriterion());
	}
}
