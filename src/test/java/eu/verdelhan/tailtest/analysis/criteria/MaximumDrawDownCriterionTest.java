package eu.verdelhan.tailtest.analysis.criteria;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import eu.verdelhan.tailtest.ConstrainedTimeSeries;
import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.TimeSeriesSlicer;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.analysis.evaluator.Decision;
import eu.verdelhan.tailtest.analysis.evaluator.DummyDecision;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.series.RegularSlicer;

import org.joda.time.Period;
import org.junit.Test;

public class MaximumDrawDownCriterionTest {

	@Test
	public void testCalculateWithNoTrades() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 1, 2, 3, 6, 5, 20, 3 });
		MaximumDrawDownCriterion mdd = new MaximumDrawDownCriterion();
		List<Trade> trades = new ArrayList<Trade>();

		assertEquals(0d, mdd.calculate(series, trades));
	}

	@Test
	public void testCalculateWithOnlyGains() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 1, 2, 3, 6, 8, 20, 3 });
		MaximumDrawDownCriterion mdd = new MaximumDrawDownCriterion();
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));

		assertEquals(0d, mdd.calculate(series, trades));
	}

	@Test
	public void testCalculateShouldWork() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 1, 2, 3, 6, 5, 20, 3 });
		MaximumDrawDownCriterion mdd = new MaximumDrawDownCriterion();
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(4, OperationType.SELL)));
		trades.add(new Trade(new Operation(5, OperationType.BUY), new Operation(6, OperationType.SELL)));

		assertEquals(.875d, mdd.calculate(series, trades));

	}

	@Test
	public void testCalculateWithNullSeriesSizeShouldReturn1() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] {});
		MaximumDrawDownCriterion mdd = new MaximumDrawDownCriterion();
		List<Trade> trades = new ArrayList<Trade>();

		assertEquals(0d, mdd.calculate(series, trades));
	}

	@Test
	public void testWithTradesThatSellBeforeBuying() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 2, 1, 3, 5, 6, 3, 20 });
		MaximumDrawDownCriterion mdd = new MaximumDrawDownCriterion();
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(4, OperationType.SELL)));
		trades.add(new Trade(new Operation(5, OperationType.SELL), new Operation(6, OperationType.BUY)));

		assertEquals(.91, mdd.calculate(series, trades));
	}

	@Test
	public void testWithSimpleTrades() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 1, 10, 5, 6, 1 });
		MaximumDrawDownCriterion mdd = new MaximumDrawDownCriterion();
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(1, OperationType.BUY), new Operation(2, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(3, OperationType.SELL)));
		trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(4, OperationType.SELL)));
		// TODO: should raise IndexOutOfBoundsException
		// trades.add(new Trade(new Operation(4, OperationType.BUY), new
		// Operation(5, OperationType.SELL)));

		assertEquals(.9d, mdd.calculate(series, trades));
	}

	@Test
	public void testSummarize() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 1, 2, 3, 6, 5, 20, 3 });
		List<Decision> decisions = new LinkedList<Decision>();
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));

		List<Trade> tradesToDummy1 = new LinkedList<Trade>();
		tradesToDummy1.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		Decision dummy1 = new DummyDecision(tradesToDummy1, slicer);
		decisions.add(dummy1);

		List<Trade> tradesToDummy2 = new LinkedList<Trade>();
		tradesToDummy2.add(new Trade(new Operation(3, OperationType.BUY), new Operation(4, OperationType.SELL)));
		Decision dummy2 = new DummyDecision(tradesToDummy2, slicer);
		decisions.add(dummy2);

		List<Trade> tradesToDummy3 = new LinkedList<Trade>();
		tradesToDummy3.add(new Trade(new Operation(5, OperationType.BUY), new Operation(6, OperationType.SELL)));
		Decision dummy3 = new DummyDecision(tradesToDummy3, slicer);
		decisions.add(dummy3);

		MaximumDrawDownCriterion mdd = new MaximumDrawDownCriterion();

		assertEquals(.875d, mdd.summarize(series, decisions));

	}
	@Test
	public void testWithConstrainedTimeSeries()
	{
		SampleTimeSeries sampleSeries = new SampleTimeSeries(new double[] {1, 1, 1, 1, 1, 10, 5, 6, 1, 1, 1 });
		ConstrainedTimeSeries series = new ConstrainedTimeSeries(sampleSeries, 4, 8);
		MaximumDrawDownCriterion mdd = new MaximumDrawDownCriterion();
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.SELL)));
		trades.add(new Trade(new Operation(5, OperationType.BUY), new Operation(6, OperationType.SELL)));
		trades.add(new Trade(new Operation(6, OperationType.BUY), new Operation(7, OperationType.SELL)));
		trades.add(new Trade(new Operation(7, OperationType.BUY), new Operation(8, OperationType.SELL)));
		assertEquals(.9d, mdd.calculate(series, trades));
		
	}
	@Test
	public void testEquals()
	{
		MaximumDrawDownCriterion criterion = new MaximumDrawDownCriterion();
		assertTrue(criterion.equals(criterion));
		assertTrue(criterion.equals(new MaximumDrawDownCriterion()));
		assertFalse(criterion.equals(new TotalProfitCriterion()));
		assertFalse(criterion.equals(5d));
		assertFalse(criterion.equals(null));
	}
}