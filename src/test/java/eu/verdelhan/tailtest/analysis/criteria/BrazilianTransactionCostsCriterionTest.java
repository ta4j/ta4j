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


public class BrazilianTransactionCostsCriterionTest {
	
	@Test
	public void testCalculate() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 105, 110, 100, 95, 105 });
		List<Trade> trades = new ArrayList<Trade>();
		AnalysisCriterion brazilianCost = new BrazilianTransactionCostsCriterion();
		
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		
		assertEquals(40d, brazilianCost.calculate(series, trades));
		
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(3, OperationType.SELL)));

		
		assertEquals(80d, brazilianCost.calculate(series, trades));
		trades.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.SELL)));
		assertEquals(120d, brazilianCost.calculate(series, trades));
	}

	@Test
	public void testCalculateWithOneTrade() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		Trade trade = new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL));
		AnalysisCriterion brazilianCost = new BrazilianTransactionCostsCriterion();
		assertEquals(40d, brazilianCost.calculate(series, trade));
	}
	@Test
	public void testSummarize() {
		DateTime date = new DateTime();
		
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 }, new DateTime[]{date, date, date, date, date, date});
		List<Trade> trades = new ArrayList<Trade>();
		List<Decision> decisions = new ArrayList<Decision>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		
		decisions.add(new Decision(null, new RegularSlicer(series, new Period().withYears(2000)), 0,null, trades, null));
		
		trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(3, OperationType.SELL)));
		trades.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.SELL)));
		
		decisions.add(new Decision(null, new RegularSlicer(series, new Period().withYears(2000)),0, null, trades, null));
		decisions.add(new Decision(null, new RegularSlicer(series, new Period().withYears(2000)),0, null, trades, null));
		
		AnalysisCriterion brazilianCosts = new BrazilianTransactionCostsCriterion();
		assertEquals(200d, brazilianCosts.summarize(series, decisions));
		
		
	}
	
	@Test
	public void testEquals()
	{
		BrazilianTransactionCostsCriterion criterion = new BrazilianTransactionCostsCriterion();
		assertTrue(criterion.equals(criterion));
		assertTrue(criterion.equals(new BrazilianTransactionCostsCriterion()));
		assertFalse(criterion.equals(new TotalProfitCriterion()));
		assertFalse(criterion.equals(5d));
		assertFalse(criterion.equals(null));
	}
	
}
