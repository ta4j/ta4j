package eu.verdelhan.ta4j.analysis.evaluator;

import eu.verdelhan.ta4j.analysis.evaluator.Decision;
import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Runner;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.criteria.AverageProfitCriterion;
import eu.verdelhan.ta4j.analysis.criteria.TotalProfitCriterion;
import eu.verdelhan.ta4j.runner.HistoryRunner;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.series.RegularSlicer;
import eu.verdelhan.ta4j.strategy.FakeStrategy;
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

		series = new MockTimeSeries(3d, 5d, 7d, 9d);
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
		series = new MockTimeSeries(3d, 1d, 7d, 9d);
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
		series = new MockTimeSeries(3d, 1d, 7d, 9d);
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
		series = new MockTimeSeries(3d, 1d, 7d, 9d);
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
		series = new MockTimeSeries(new double[] { 1d, 2d, 3d, 4d, 5d,5d, 5d, 5d, 5d, 5d},new DateTime[]{date.withYear(2000),date.withYear(2000),date.withYear(2000),date.withYear(2000),date.withYear(2000),date.withYear(2001),date.withYear(2001),date.withYear(2001),date.withYear(2001),date.withYear(2001),});
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
