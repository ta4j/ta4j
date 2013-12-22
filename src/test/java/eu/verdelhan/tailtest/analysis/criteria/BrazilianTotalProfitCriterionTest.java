package net.sf.tail.analysis.criteria;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.Operation;
import net.sf.tail.OperationType;
import net.sf.tail.Trade;
import net.sf.tail.analysis.evaluator.Decision;
import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.series.RegularSlicer;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Test;


public class BrazilianTotalProfitCriterionTest {
	@Test
	public void testCalculateOnlyWithGainTrades() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 105, 110, 100, 95, 105 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion profit = new BrazilianTotalProfitCriterion();
		assertEquals(((110 * 0.99965d) / (100 * 1.00035d)) * ((105 * 0.99965d) / (100 * 1.00035d)), profit.calculate(series, trades));
	}

	@Test
	public void testCalculateOnlyWithLossTrades() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion profit = new BrazilianTotalProfitCriterion();
		assertEquals(((95 * 0.99965d) / (100 * 1.00035d)) * ((70 * 0.99965d) / (100 * 1.00035d)), profit.calculate(series, trades));
	}

	@Test
	public void testCalculateProfitWithTradesThatStartSelling() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.SELL), new Operation(1, OperationType.BUY)));
		trades.add(new Trade(new Operation(2, OperationType.SELL), new Operation(5, OperationType.BUY)));

		AnalysisCriterion profit = new BrazilianTotalProfitCriterion();
		assertEquals(((100 * 0.99965d) / (95d * 1.00035d)) * ((100 * 0.99965d) / (70 * 1.00035d)), profit.calculate(series, trades));
	}

	@Test
	public void testCalculateWithNoTradesShouldReturn1() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		List<Trade> trades = new ArrayList<Trade>();

		AnalysisCriterion profit = new BrazilianTotalProfitCriterion();
		assertEquals(1d, profit.calculate(series, trades));
	}
	
	@Test
	public void testSummarize()
	{
		DateTime date = new DateTime();
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 }, new DateTime[]{date, date, date, date, date, date});
		List<Trade> trades = new ArrayList<Trade>();
		List<Decision> decisions = new ArrayList<Decision>();
		AnalysisCriterion profit = new BrazilianTotalProfitCriterion();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));
		decisions.add(new Decision(null, new RegularSlicer(series, new Period().withYears(2000)),0, null, trades, null));
		
		
		
		trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.SELL), new Operation(1, OperationType.BUY)));
		trades.add(new Trade(new Operation(2, OperationType.SELL), new Operation(5, OperationType.BUY)));

		double value1 = ((100 * 0.99965d) / (95d * 1.00035d)) * ((100 * 0.99965d) / (70 * 1.00035d));
		double value2 = ((95 * 0.99965d) / (100 * 1.00035d)) * ((70 * 0.99965d) / (100 * 1.00035d));
		decisions.add(new Decision(null, new RegularSlicer(series, new Period().withYears(2000)),0, null, trades, null));
		assertEquals(value1 * value2, profit.summarize(series, decisions));
						
	}
	
	@Test
	public void testWithOneTrade()
	{
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		
		Trade trade = new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL));
		
		AnalysisCriterion profit = new BrazilianTotalProfitCriterion();
		assertEquals(((95 * 0.99965d) / (100 * 1.00035d)), profit.calculate(series, trade));
	}
	@Test
	public void testEquals()
	{
		BrazilianTotalProfitCriterion criterion = new BrazilianTotalProfitCriterion();
		assertTrue(criterion.equals(criterion));
		assertTrue(criterion.equals(new BrazilianTotalProfitCriterion()));
		assertFalse(criterion.equals(new TotalProfitCriterion()));
		assertFalse(criterion.equals(5d));
		assertFalse(criterion.equals(null));
	}
	
}
