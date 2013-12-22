package net.sf.tail.analysis.criteria;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.Operation;
import net.sf.tail.OperationType;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.Trade;
import net.sf.tail.analysis.evaluator.Decision;
import net.sf.tail.analysis.evaluator.DummyDecision;
import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.series.RegularSlicer;

import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class AverageProfitCriterionTest {
	private SampleTimeSeries series;

	private List<Trade> trades;

	@Before
	public void setUp() throws Exception {
		trades = new ArrayList<Trade>();
	}

	@Test
	public void testCalculateOnlyWithGainTrades() {
		series = new SampleTimeSeries(100d, 105d, 110d, 100d, 95d, 105d);
		trades.clear();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));
		AnalysisCriterion averageProfit = new AverageProfitCriterion();
		assertEquals(1.03, averageProfit.calculate(series, trades), 0.01);
	}

	@Test
	public void testSummarize() {
		series = new SampleTimeSeries(100d, 105d, 110d, 100d, 95d, 105d);
		List<Decision> decisions = new LinkedList<Decision>();
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));

		List<Trade> tradesToDummy1 = new LinkedList<Trade>();
		tradesToDummy1.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		Decision dummy1 = new DummyDecision(tradesToDummy1, slicer);
		decisions.add(dummy1);

		List<Trade> tradesToDummy2 = new LinkedList<Trade>();
		tradesToDummy2.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));
		Decision dummy2 = new DummyDecision(tradesToDummy2, slicer);
		decisions.add(dummy2);

		AnalysisCriterion averageProfit = new AverageProfitCriterion();
		assertEquals(1.03, averageProfit.summarize(series, decisions), 0.01);
	}

	@Test
	public void testCalculateWithASimpleTrade() {
		series = new SampleTimeSeries(100d, 105d, 110d, 100d, 95d, 105d);
		trades.clear();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		AnalysisCriterion averageProfit = new AverageProfitCriterion();
		assertEquals(Math.pow(110d/100, 1d/3), averageProfit.calculate(series, trades), 0.001);
	}

	@Test
	public void testCalculateOnlyWithLossTrades() {
		series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		trades.clear();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));
		AnalysisCriterion averageProfit = new AverageProfitCriterion();
		assertEquals(Math.pow(95d/100 * 70d/100, 1d / 6), averageProfit.calculate(series, trades), 0.01);
	}

	@Test
	public void testCalculateWithNoTicksShouldReturn1() {
		series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		trades.clear();
		AnalysisCriterion averageProfit = new AverageProfitCriterion();
		assertEquals(1d, averageProfit.calculate(series, trades));
	}
	@Test
	public void testCalculateWithOneTrade()
	{
		series = new SampleTimeSeries(new double[] {100, 105});
		Trade trade = new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL));
		AnalysisCriterion average = new AverageProfitCriterion();
		assertEquals(Math.pow(105d / 100, 1d/2), average.calculate(series, trade));
		
	}
	@Test
	public void testEquals()
	{
		AverageProfitCriterion criterion = new AverageProfitCriterion();
		assertTrue(criterion.equals(criterion));
		assertTrue(criterion.equals(new AverageProfitCriterion()));
		assertFalse(criterion.equals(new TotalProfitCriterion()));
		assertFalse(criterion.equals(5d));
		assertFalse(criterion.equals(null));
	}

}
