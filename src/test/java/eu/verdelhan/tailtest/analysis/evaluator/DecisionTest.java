package net.sf.tail.analysis.evaluator;

import static org.junit.Assert.assertEquals;

import java.util.List;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.Operation;
import net.sf.tail.OperationType;
import net.sf.tail.Runner;
import net.sf.tail.Strategy;
import net.sf.tail.TimeSeries;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.Trade;
import net.sf.tail.analysis.criteria.AverageProfitCriterion;
import net.sf.tail.analysis.criteria.TotalProfitCriterion;
import net.sf.tail.runner.HistoryRunner;
import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.series.RegularSlicer;
import net.sf.tail.strategy.FakeStrategy;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class DecisionTest {

	private TimeSeries series;

	private AnalysisCriterion criteria;

	@Before
	public void setUp() {
		criteria = new TotalProfitCriterion();
	}

	@Test
	public void testEvaluateCriterion() {

		series = new SampleTimeSeries(3d, 5d, 7d, 9d);
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));

		Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null,
				new Operation(2, OperationType.BUY), null };

		Operation[] sell = new Operation[] { null, new Operation(1, OperationType.SELL), null,
				new Operation(3, OperationType.SELL) };

		Strategy fakeStrategy = new FakeStrategy(buy, sell);
		
		Runner runner = new HistoryRunner(slicer,fakeStrategy);

		Decision decision = new Decision(fakeStrategy, slicer,0, criteria, runner.run(0), new HistoryRunner(slicer,fakeStrategy));
		assertEquals(45d / 21, decision.evaluateCriterion(), 0.001);
	}

	@Test
	public void testEvaluateCriterionNotSelling() {
		series = new SampleTimeSeries(3d, 1d, 7d, 9d);
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
		
		Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null,
				new Operation(2, OperationType.BUY), null };

		Operation[] sell = new Operation[] { null, null, null, null };

		Strategy fakeStrategy = new FakeStrategy(buy, sell);
		Runner runner = new HistoryRunner(slicer,fakeStrategy);
		Decision decision = new Decision(fakeStrategy, slicer,0, criteria, runner.run(0), new HistoryRunner(slicer,fakeStrategy));
		assertEquals(1d, decision.evaluateCriterion());
	}

	@Test
	public void testEvaluateCriterionWithAnotherCriteria() {
		series = new SampleTimeSeries(3d, 1d, 7d, 9d);
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
		
		Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null, null, null };

		Operation[] sell = new Operation[] { null, null, null, new Operation(3, OperationType.SELL) };

		Strategy fakeStrategy = new FakeStrategy(buy, sell);
		Runner runner = new HistoryRunner(slicer,fakeStrategy);
		Decision decision = new Decision(fakeStrategy, slicer,0, null, runner.run(0), new HistoryRunner(slicer,fakeStrategy));
		assertEquals(Math.pow(3d, 1d / 4), decision.evaluateCriterion(new AverageProfitCriterion()), 0.0001);
	}
	
	@Test
	public void testAverageProfitWithZeroNumberOfTicks() {
		series = new SampleTimeSeries(3d, 1d, 7d, 9d);
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
		
		Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null,
				new Operation(2, OperationType.BUY), null };

		Operation[] sell = new Operation[] { null, null, null, null };

		Strategy fakeStrategy = new FakeStrategy(buy, sell);
		Runner runner = new HistoryRunner(slicer,fakeStrategy);
		Decision decision = new Decision(fakeStrategy, slicer,0, null, runner.run(0), new HistoryRunner(slicer,fakeStrategy));
		assertEquals(1d, decision.evaluateCriterion(new AverageProfitCriterion()));
	}

	@Test
	public void testApplyFor() {
		DateTime date = new DateTime();
		series = new SampleTimeSeries(new double[] { 1d, 2d, 3d, 4d, 5d,5d, 5d, 5d, 5d, 5d},new DateTime[]{date.withYear(2000),date.withYear(2000),date.withYear(2000),date.withYear(2000),date.withYear(2000),date.withYear(2001),date.withYear(2001),date.withYear(2001),date.withYear(2001),date.withYear(2001),});
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));

		Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null, null, null, null, null, null, null, null,null };
		Operation[] sell = new Operation[] { null, null, null, null, new Operation(4, OperationType.SELL), null, null, null, null,null };
		Strategy fakeStrategy = new FakeStrategy(buy, sell);
		Runner runner = new HistoryRunner(slicer,fakeStrategy);
		List<Trade> trades = runner.run(0);
		Decision decision = new Decision(fakeStrategy, slicer,0, criteria, trades, new HistoryRunner(slicer,fakeStrategy));
		Decision nextDecision = new Decision(fakeStrategy, slicer,1, criteria, runner.run(1), new HistoryRunner(slicer,fakeStrategy));

		Decision appliedDecision = decision.applyFor(1);

		assertEquals(nextDecision, appliedDecision);
		assertEquals(1d, appliedDecision.evaluateCriterion());
		assertEquals(slicer.getSlice(1).getBegin(), appliedDecision.getActualSlice().getBegin());
		assertEquals(slicer.getSlice(1).getEnd(), appliedDecision.getActualSlice().getEnd());
	}
}
