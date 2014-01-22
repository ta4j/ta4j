package eu.verdelhan.tailtest.analysis.criteria;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import eu.verdelhan.tailtest.AnalysisCriterion;
import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.TimeSeriesSlicer;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.analysis.evaluator.Decision;
import eu.verdelhan.tailtest.analysis.evaluator.MockDecision;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.series.RegularSlicer;

import org.joda.time.Period;
import org.junit.Test;

public class VersusBuyAndHoldCriterionTest {

	@Test
	public void testCalculateOnlyWithGainTrades() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 105, 110, 100, 95, 105 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion buyAndHold = new VersusBuyAndHoldCriterion(new TotalProfitCriterion());
		assertEquals(1.10 * 1.05 / 1.05, buyAndHold.calculate(series, trades));
	}

	@Test
	public void testSummarize() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 105, 110, 100, 95, 105 });
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
		List<Decision> decisions = new LinkedList<Decision>();

		List<Trade> tradesToDummy1 = new LinkedList<Trade>();
		tradesToDummy1.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		Decision dummy1 = new MockDecision(tradesToDummy1, slicer);
		decisions.add(dummy1);

		List<Trade> tradesToDummy2 = new LinkedList<Trade>();
		tradesToDummy2.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));
		Decision dummy2 = new MockDecision(tradesToDummy2, slicer);
		decisions.add(dummy2);
		AnalysisCriterion buyAndHold = new VersusBuyAndHoldCriterion(new TotalProfitCriterion());
		assertEquals(1.10 * 1.05 / 1.05, buyAndHold.summarize(series, decisions), 0.01);
	}

	@Test
	public void testCalculateOnlyWithLossTrades() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion buyAndHold = new VersusBuyAndHoldCriterion(new TotalProfitCriterion());
		assertEquals(0.95 * 0.7 / 0.7, buyAndHold.calculate(series, trades));
	}
	
	@Test
	public void testCalculateWithOnlyOneTrade() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		Trade trade = new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL));

		AnalysisCriterion buyAndHold = new VersusBuyAndHoldCriterion(new TotalProfitCriterion());
		assertEquals((100d / 70) / (100d / 95), buyAndHold.calculate(series, trade));
	}

	@Test
	public void testCalculateWithNoTrades() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		List<Trade> trades = new ArrayList<Trade>();

		AnalysisCriterion buyAndHold = new VersusBuyAndHoldCriterion(new TotalProfitCriterion());
		assertEquals(1 / 0.7, buyAndHold.calculate(series, trades));
	}
	@Test
	public void testCalculateWithAverageProfit()
	{
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 130 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion buyAndHold = new VersusBuyAndHoldCriterion(new AverageProfitCriterion());
		
		assertEquals(Math.pow(95d/100 * 130d/100, 1d/6) / Math.pow(130d / 100, 1d/6), buyAndHold.calculate(series, trades) ,0.0001);
	}
	@Test
	public void testCalculateWithNumberOfTicks()
	{
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 130 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion buyAndHold = new VersusBuyAndHoldCriterion(new NumberOfTicksCriterion());
		
		assertEquals(6d/6d, buyAndHold.calculate(series, trades));
	}
}
