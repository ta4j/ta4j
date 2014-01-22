package eu.verdelhan.tailtest.analysis.evaluator;

import eu.verdelhan.tailtest.AnalysisCriterion;
import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.Runner;
import eu.verdelhan.tailtest.Strategy;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.TimeSeriesSlicer;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.analysis.criteria.AverageProfitCriterion;
import eu.verdelhan.tailtest.analysis.criteria.TotalProfitCriterion;
import eu.verdelhan.tailtest.runner.HistoryRunner;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.series.RegularSlicer;
import eu.verdelhan.tailtest.strategy.FakeStrategy;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.Period;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class DecisionTest {

	private TimeSeries series;

	private AnalysisCriterion criterion;

	@Before
	public void setUp() {
		criterion = new TotalProfitCriterion();
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

		Decision decision = new Decision(fakeStrategy, slicer,0, criterion, runner.run(0), new HistoryRunner(slicer,fakeStrategy));
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
		Decision decision = new Decision(fakeStrategy, slicer,0, criterion, runner.run(0), new HistoryRunner(slicer,fakeStrategy));
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
		Decision decision = new Decision(fakeStrategy, slicer,0, criterion, trades, new HistoryRunner(slicer,fakeStrategy));
		Decision nextDecision = new Decision(fakeStrategy, slicer,1, criterion, runner.run(1), new HistoryRunner(slicer,fakeStrategy));

		Decision appliedDecision = decision.applyFor(1);

		assertEquals(nextDecision, appliedDecision);
		assertEquals(1d, appliedDecision.evaluateCriterion());
		assertEquals(slicer.getSlice(1).getBegin(), appliedDecision.getActualSlice().getBegin());
		assertEquals(slicer.getSlice(1).getEnd(), appliedDecision.getActualSlice().getEnd());
	}
}
